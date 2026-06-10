package com.epam.doable1.service;

import com.epam.doable1.model.CourseEnrollment;
import com.epam.doable1.model.Employee;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseCompletionService {

    /**
     * Returns active employees who are enrolled in a course but have NOT completed it.
     * Employees present in the course sheet but absent from the active employee sheet are excluded.
     */
    public List<IncompleteRecord> getIncompleteActiveEmployees(
            List<Employee> activeEmployees,
            List<CourseEnrollment> enrollments) {

        Map<String, Employee> activeMap = activeEmployees.stream()
                .collect(Collectors.toMap(
                        e -> e.getEmployeeId().toLowerCase(),
                        e -> e,
                        (existing, duplicate) -> existing
                ));

        List<IncompleteRecord> incomplete = new ArrayList<>();

        for (CourseEnrollment enrollment : enrollments) {
            if (enrollment.isCompleted()) continue;

            String empId   = enrollment.getEmployeeId().toLowerCase();
            Employee employee = activeMap.get(empId);
            if (employee == null) continue;

            incomplete.add(new IncompleteRecord(
                    employee,
                    enrollment.getCourseName(),
                    enrollment.getCourseId(),
                    enrollment.getMentorEmail()
            ));
        }

        return incomplete;
    }

    public record IncompleteRecord(Employee employee, String courseName, String courseId, String mentorEmail) {}
}

