package com.epam.doable1.service;

import com.epam.doable1.model.CourseEnrollment;
import com.epam.doable1.model.Employee;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelParserService {

    /**
     * Parses the active employee sheet.
     * Expected columns (0-indexed):
     * 0: EmployeeId, 1: Name, 2: Email, 3: Department
     */
    public List<Employee> parseActiveEmployees(MultipartFile file) throws IOException {
        List<Employee> employees = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;

            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue;
                }
                if (isRowEmpty(row)) continue;

                String employeeId = getCellValue(row, 0);
                String name       = getCellValue(row, 1);
                String email      = getCellValue(row, 2).toLowerCase().trim();
                String department = getCellValue(row, 3);

                employees.add(new Employee(employeeId, name, email, department));
            }
        }
        return employees;
    }

    /**
     * Parses the course progress sheet.
     * Expected columns (0-indexed):
     * 0: EmployeeId, 1: Email, 2: Department, 3: CourseName, 4: CourseId,
     * 5: MentorEmail, 6: Progress ("Completed" / "Not Completed")
     */
    public List<CourseEnrollment> parseCourseEnrollments(MultipartFile file) throws IOException {
        List<CourseEnrollment> enrollments = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean firstRow = true;

            for (Row row : sheet) {
                if (firstRow) {
                    firstRow = false;
                    continue;
                }
                if (isRowEmpty(row)) continue;

                String employeeId  = getCellValue(row, 0);
                String email       = getCellValue(row, 1).toLowerCase().trim();
                String department  = getCellValue(row, 2);
                String courseName  = getCellValue(row, 3);
                String courseId    = getCellValue(row, 4);
                String mentorEmail = getCellValue(row, 5).toLowerCase().trim();
                String progressStr = getCellValue(row, 6).toLowerCase().trim();
                boolean completed  = progressStr.equals("completed") || progressStr.equals("yes")
                        || progressStr.equals("true") || progressStr.equals("1");

                enrollments.add(new CourseEnrollment(employeeId, email, department,
                        courseName, courseId, mentorEmail, completed));
            }
        }
        return enrollments;
    }

    private String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}

