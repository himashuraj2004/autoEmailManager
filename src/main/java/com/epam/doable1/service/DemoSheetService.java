package com.epam.doable1.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates in-memory demo Excel files for both sheets so users
 * can immediately try the application without preparing real data.
 */
@Service
public class DemoSheetService {

    /**
     * Active Employee sheet: EmployeeId | Name | Email | Department
     */
    public byte[] generateActiveEmployeeSheet() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Active Employees");

            CellStyle headerStyle = createHeaderStyle(wb);

            String[] headers = {"EmployeeId", "Name", "Email", "Department"};
            createHeaderRow(sheet, headers, headerStyle);

            Object[][] data = {
                {"EMP001", "Alice Johnson",   "alice.johnson@company.com",   "Engineering"},
                {"EMP002", "Bob Smith",       "bob.smith@company.com",       "Engineering"},
                {"EMP003", "Carol White",     "carol.white@company.com",     "Marketing"},
                {"EMP004", "David Brown",     "david.brown@company.com",     "Marketing"},
                {"EMP005", "Eva Green",       "eva.green@company.com",       "HR"},
                {"EMP006", "Frank Miller",    "frank.miller@company.com",    "Engineering"},
            };
            writeDataRows(sheet, data);
            autoSizeColumns(sheet, headers.length);

            return toBytes(wb);
        }
    }

    /**
     * Course Progress sheet:
     * EmployeeId | Email | Department | CourseName | CourseId | MentorEmail | Progress
     */
    public byte[] generateCourseProgressSheet() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Course Progress");

            CellStyle headerStyle = createHeaderStyle(wb);

            String[] headers = {
                "EmployeeId", "Email", "Department",
                "CourseName", "CourseId", "MentorEmail", "Progress"
            };
            createHeaderRow(sheet, headers, headerStyle);

            Object[][] data = {
                {"EMP001", "alice.johnson@company.com",  "Engineering", "Java Fundamentals",      "CRS101", "mentor.a@company.com", "Not Completed"},
                {"EMP002", "bob.smith@company.com",      "Engineering", "Java Fundamentals",      "CRS101", "mentor.a@company.com", "Completed"},
                {"EMP003", "carol.white@company.com",    "Marketing",   "Digital Marketing 101",  "CRS202", "mentor.b@company.com", "Not Completed"},
                {"EMP004", "david.brown@company.com",    "Marketing",   "Digital Marketing 101",  "CRS202", "mentor.b@company.com", "Not Completed"},
                {"EMP005", "eva.green@company.com",      "HR",          "HR Compliance Training", "CRS303", "mentor.b@company.com", "Completed"},
                {"EMP006", "frank.miller@company.com",   "Engineering", "Cloud Architecture",     "CRS404", "mentor.a@company.com", "Not Completed"},
                // Former employee (not in active list) — should be excluded from intersection
                {"EMP099", "ex.employee@company.com",    "Finance",     "Finance Basics",         "CRS505", "mentor.a@company.com", "Not Completed"},
            };
            writeDataRows(sheet, data);
            autoSizeColumns(sheet, headers.length);

            return toBytes(wb);
        }
    }

    // ------------------------------------------------------------------ //

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRows(Sheet sheet, Object[][] data) {
        for (int r = 0; r < data.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < data[r].length; c++) {
                row.createCell(c).setCellValue(String.valueOf(data[r][c]));
            }
        }
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
