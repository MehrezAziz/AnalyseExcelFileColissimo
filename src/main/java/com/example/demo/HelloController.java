package com.example.demo;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloController {

    @FXML
    private Label welcomeText;

    @FXML
    private VBox dropArea;

    @FXML
    private Label otherProducts;

    @FXML
    private Label affichageSec;

    @FXML
    private Label RetourEchange;

    @FXML
    private TextField sentenceField;

    @FXML
    private TextArea savedSentencesArea;

    @FXML
    private ScrollPane scrollpaneAutre;

    @FXML
    private ScrollPane echangeRetour;

    private ScrollPane sc= new ScrollPane();

    private ArrayList<Hashtable<String, Double>> produits = new ArrayList<>();

    private double totalBenefit=0;

    String affichage="";
    String affichageSecondaire="";
    String echange="Echange & Retour: ";


    @FXML
    private void initialize() {
        dropArea.setOnDragOver(this::handleDragOver);
        dropArea.setOnDragDropped(this::handleDragDropped);
        dropArea.setOnMouseClicked(event -> openFileChooser());
        loadSavedSentences();
    }

    private void handleDragOver(DragEvent event) {
        if (event.getGestureSource() != dropArea && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            List<File> files = db.getFiles();
            handleFile(files.get(0));
        }
        event.setDropCompleted(success);
        event.consume();
    }

    private void openFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xls", "*.xlsx")
        );
        File selectedFile = fileChooser.showOpenDialog(dropArea.getScene().getWindow());
        if (selectedFile != null) {
            handleFile(selectedFile);
        }
    }

    private void handleFile(File file) {
        try {
            if (!isExcelFile(file) || !isValidExcelFile(file)) {
                welcomeText.setText("Invalid file type or file signature. Please upload a valid Excel file.");
                return;
            }

            int[] rowCounts = countRows(file);

            welcomeText.setText(affichage);  affichage="";
        } catch (IOException e) {
            welcomeText.setText("Error reading file: " + e.getMessage());
        }
    }

    private boolean isExcelFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".xls") || fileName.endsWith(".xlsx");
    }

    private boolean isValidExcelFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[8];
            if (fis.read(header) != 8) {
                return false;
            }

            String fileSignature = String.format("%02X%02X%02X%02X", header[0], header[1], header[2], header[3]);
            return fileSignature.equals("D0CF11E0") || fileSignature.equals("504B0304");
        } catch (IOException e) {
            return false;
        }
    }

    private int[] countRows(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        Workbook workbook = null;

        try {
            if (file.getName().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (file.getName().endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IllegalArgumentException("The specified file is not an Excel file");
            }

            Sheet sheet = workbook.getSheetAt(0);
            int firstTableRows = countFirstTableRows(sheet);
            int secondTableRows = countSecondTableRows(sheet);

            return new int[]{firstTableRows, secondTableRows};

        } finally {
            if (workbook != null) {
                workbook.close();
            }
            fis.close();
        }
    }

    private int countFirstTableRows(Sheet sheet) {
        int startRow = 8; // A9 corresponds to the 8th index
        int rowCount = 0;
        String searchString = "Détails des colis :";

        for (int i = startRow; true; i++) {
            Row row = sheet.getRow(i);
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING &&
                    searchString.equals(cell.getStringCellValue())) {
                break;
            }
            if (row == null || isRowEmpty(row) ) {
                break;
            }
            rowCount++;
        }
        return rowCount;
    }

    private int countSecondTableRows(Sheet sheet) {
        String searchString = "Détails des colis :";
        int startRow = 0;

        int nbrRetourEchange=0;
        int nbrAutre=0;

        for (int i = 0; true; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(0); // Assuming the search string is in the first column
                if (cell != null && cell.getCellType() == CellType.STRING &&
                        searchString.equals(cell.getStringCellValue())) {
                    startRow = i + 1;
                    break;
                }
            }
        }

        // If the search string was not found, return 0
        if (startRow == 0) {
            return 0; // "Détails des colis :" not found
        }

        int rowCount = 0;
        String allOtherProducts="Autre:";
        // Process rows starting from startRow + 1
        for (int i = startRow+1; true; i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                break;
            }
            rowCount++;

            // Extract designation (column 9) and montant (column 11)
            Cell designationCell = row.getCell(9);
            Cell montantCell = row.getCell(11);

            String designation = "";
            double montant = 0.0;

            // Check cell types before extracting values
            if (designationCell != null) {
                if (designationCell.getCellType() == CellType.STRING) {
                    designation = designationCell.getStringCellValue();
                } else if (designationCell.getCellType() == CellType.NUMERIC) {
                    System.out.println("\nerror here: "+designation+"\n");
                    designation = String.valueOf(designationCell.getNumericCellValue());
                }
            }

            if (montantCell != null) {
                if (montantCell.getCellType() == CellType.NUMERIC) {
                    montant = montantCell.getNumericCellValue();
                } else if (montantCell.getCellType() == CellType.STRING) {
                    try {
                        System.out.println("\nerror here: "+montant+"\n");

                        montant = Double.parseDouble(montantCell.getStringCellValue().replace(',', '.'));
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Cannot parse number from '" + montantCell.getStringCellValue() + "'. Continuing with 0.0.");
                        montant = 0.0;
                    }
                }
            }
            List <Hashtable<Integer,String>> quantiteNom;
            quantiteNom= parseMultipleStrings(designation);
            totalBenefit += montant;
            if(containsIgnoreCase(designation,"echange")
                    || (containsIgnoreCase(designation,"Échange"))
                    || (montant<=0)
            ){


                echange+="\n"+ ++nbrRetourEchange+":  "+designation +" , \"Montant Coliisimo\": "+montant/1000+" dt";
                //totalBenefit+= montant;
                continue;
            }

            for (Hashtable<Integer, String> hashtable : quantiteNom) {
                for (Map.Entry<Integer, String> entry : hashtable.entrySet()) {
                    Integer key = entry.getKey();
                    String designa = entry.getValue();

                    double priceCorresponding = findMatchingValue(designa, produits);
                    if (priceCorresponding != -999.0) {
                        affichageSecondaire+=key+" x  " + designa + ", \"Ras Lmal\": "+key*priceCorresponding+" dt\n";
                        System.out.println(affichageSecondaire);
                        totalBenefit -= key*priceCorresponding*1000 ; // Adjust as per your business logic
                    }else{

                        allOtherProducts+="\n"+ ++nbrAutre+":  "+designa;
                    }
                }
            }

        } //end "for"' loop
        sc.setContent(affichageSec);
        affichageSec.setText(affichageSecondaire);
        if(allOtherProducts.equals("Autre:")){
            scrollpaneAutre.setVisible(false);
            scrollpaneAutre.setMaxSize(0,0);
        }else{
            scrollpaneAutre.setVisible(true);
            scrollpaneAutre.setMaxSize(550,120);
        }
        RetourEchange.setText(echange);
        otherProducts.setText(allOtherProducts.equals("Autre:")?"":allOtherProducts);
        affichage+="benefit totale: "+totalBenefit/1000+" dt"; totalBenefit=0;
        return rowCount;
    }

    public static double findMatchingValue(String designation, ArrayList<Hashtable<String, Double>> liste) {
        for (Hashtable<String, Double> map : liste) {
            for (String key : map.keySet()) {
                if (containsIgnoreCase(designation, key)) {
                    return map.get(key);
                }
            }
        }
        return -999.0;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    @FXML

    private void saveSentence() {
        String newSentence = sentenceField.getText();
        if (newSentence == null || newSentence.trim().isEmpty()) {
            return;
        }

        File file = new File("sentences.txt");
        List<String> sentences = new ArrayList<>();

        // Read the existing sentences
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sentences.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Check if the sentence exists and update it if necessary
        boolean updated = false;
        for (int i = 0; i < sentences.size(); i++) {
            String existingSentence = sentences.get(i);
            // Extract the base sentence part without the number in parentheses
            String existingBase = existingSentence.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
            String newBase = newSentence.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
            if (existingBase.equalsIgnoreCase(newBase)) {
                sentences.set(i, newSentence);
                updated = true;
                break;
            }
        }

        // If the sentence does not exist, add it to the list
        if (!updated) {
            sentences.add(newSentence);
        }

        // Write the updated sentences back to the file
        try (FileWriter fw = new FileWriter(file);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            for (String sentence : sentences) {
                out.println(sentence);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        sentenceField.clear();
        loadSavedSentences();
    }


    private void loadSavedSentences() {
        File file = new File("sentences.txt");
        if (!file.exists()) {
            return;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {

                Hashtable <String,Double> dic=extractNumbersFromParentheses(line);
                produits.add(dic);
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        produits=sortDictionariesByStringLength(produits);
        System.out.println("sorted products: "+produits);
        savedSentencesArea.setText(content.toString());
    }
    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }

        final int length = needle.length();
        if (length == 0) {
            return true;
        }

        for (int i = haystack.length() - length; i >= 0; i--) {
            if (haystack.regionMatches(true, i, needle, 0, length)) {
                return true;
            }
        }
        return false;
    }



    public static Hashtable<Integer, String> parseString(String input) {
        Hashtable<Integer, String> result = new Hashtable<>();

        // Define a regex pattern to match the format: "number * string"
        Pattern patternWithAsterisk = Pattern.compile("\\s*(\\d+)\\s*\\*\\s*(.+)\\s*");
        Matcher matcherWithAsterisk = patternWithAsterisk.matcher(input);

        if (matcherWithAsterisk.matches()) {
            try {
                int number = Integer.parseInt(matcherWithAsterisk.group(1));
                String text = matcherWithAsterisk.group(2).trim();
                result.put(number, text);
            } catch (NumberFormatException e) {
                System.err.println("Error parsing number: " + e.getMessage());
            }
        } else {
            // If there's no *, default to 1
            String trimmedInput = input.trim();
            if (!trimmedInput.isEmpty()) {
                result.put(1, trimmedInput);
            } else {
                System.err.println("Input string is empty or invalid.");
            }
        }

        return result;
    }

    public static List<Hashtable<Integer, String>> parseMultipleStrings(String input) {
        List<Hashtable<Integer, String>> resultList = new ArrayList<>();

        // Split the input string by '+'
        String[] parts = input.split("\\+");

        for (String part : parts) {
            // Trim each part and parse it
            Hashtable<Integer, String> parsedPart = parseString(part.trim());
            resultList.add(parsedPart);
        }

        return resultList;
    }


    public static Hashtable<String, Double> extractNumbersFromParentheses(String sentence) {
        Hashtable<String, Double> result = new Hashtable<>();

        int startIndex = -1;
        int endIndex = -1;
        String key = null;

        for (int i = 0; i < sentence.length(); i++) {
            if (sentence.charAt(i) == '(') {
                key = sentence.substring(0, i).trim();
                startIndex = i + 1;

                for (int j = i + 1; j < sentence.length(); j++) {
                    if (sentence.charAt(j) == ')') {
                        endIndex = j;
                        String numberStr = sentence.substring(startIndex, endIndex).trim();
                        try {
                            double number = Double.parseDouble(numberStr);
                            result.put(key, number);
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Cannot parse number from '" + numberStr + "'. Continuing search.");
                            startIndex = -1;
                            endIndex = -1;
                            break;
                        }
                    }
                }
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No valid number found in parentheses");
        }

        return result;
    }

    public static ArrayList<Hashtable<String, Double>> sortDictionariesByStringLength(ArrayList<Hashtable<String, Double>> inputList) {
        // Sort the list based on the length of the keys (strings) in descending order
        inputList.sort((dict1, dict2) -> {
            String key1 = dict1.keys().nextElement();
            String key2 = dict2.keys().nextElement();
            return Integer.compare(key2.length(), key1.length()); // Compare lengths in descending order
        });

        return inputList;
    }

}
