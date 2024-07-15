package com.example.demo;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

public class HelloController {

    @FXML
    private Label welcomeText;

    @FXML
    private VBox dropArea;

    @FXML
    private TextField sentenceField;

    @FXML
    private TextArea savedSentencesArea;

    private ArrayList<Hashtable<String, Double>> produits = new ArrayList<>();

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
            welcomeText.setText("Number of rows in the first table: " + rowCounts[0] +
                    "\nNumber of rows in the second table: " + rowCounts[1]);
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
        int startRow = 7; // A8 corresponds to the 7th index
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
        if (startRow == 0) {
            return 0; // "Détails des colis :" not found
        }

        int rowCount = 0;
        for (int i = startRow ; true; i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                break;
            }
            rowCount++;
        }
        return rowCount;
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
        String sentence = sentenceField.getText();
        if (sentence == null || sentence.trim().isEmpty()) {
            return;
        }

        try (FileWriter fw = new FileWriter("sentences.txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(sentence);
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
