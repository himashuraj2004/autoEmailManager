package com.epam.doable1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollment {

    private String employeeId;
    private String email;
    private String department;
    private String courseName;
    private String courseId;
    private String mentorEmail;
    private boolean completed;
}
