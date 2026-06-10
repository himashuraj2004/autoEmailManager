package com.epam.doable1.service;

import com.epam.doable1.model.SmtpConfig;
import com.epam.doable1.service.CourseCompletionService.IncompleteRecord;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a JavaMailSender at request-time from the user-supplied SMTP config and
 * dispatches HTML emails (employee reminder, mentor report, CEO master list).
 */
@Service
public class DynamicMailService {

    private final LlmEmailService llmEmailService;

    public DynamicMailService(LlmEmailService llmEmailService) {
        this.llmEmailService = llmEmailService;
    }

    public void sendNotifications(List<IncompleteRecord> incompleteRecords, SmtpConfig config) throws Exception {
        JavaMailSenderImpl mailSender = buildMailSender(config);

        // 1. Email each individual employee
        for (IncompleteRecord record : incompleteRecords) {
            String intro = llmEmailService.employeeIntro(
                    record.employee().getName(), record.courseName(), record.courseId());
            String html = buildEmployeeEmail(record, intro);
            send(mailSender, config.getFromEmail(), record.employee().getEmail(),
                    "Action Required: Complete Your Course – " + record.courseName(), html);
        }

        // 2. Group by mentor email and send one mail per mentor
        Map<String, List<IncompleteRecord>> byMentor = incompleteRecords.stream()
                .filter(r -> r.mentorEmail() != null && !r.mentorEmail().isBlank())
                .collect(Collectors.groupingBy(r -> r.mentorEmail().toLowerCase()));

        for (Map.Entry<String, List<IncompleteRecord>> entry : byMentor.entrySet()) {
            String mentorEmail = entry.getKey();
            List<IncompleteRecord> menteeRecords = entry.getValue();
            String mentorName = deriveMentorName(mentorEmail);
            String intro = llmEmailService.mentorIntro(mentorName, menteeRecords.size());
            String html = buildMentorEmail(mentorName, menteeRecords, intro);
            send(mailSender, config.getFromEmail(), mentorEmail,
                    "Pending Course Completions – Your Mentees", html);
        }

        // 3. Send master report to CEO
        long deptCount = incompleteRecords.stream()
                .map(r -> r.employee().getDepartment())
                .distinct().count();
        String ceoIntro = llmEmailService.ceoIntro(
                config.getCeoName(), incompleteRecords.size(), (int) deptCount);
        String ceoHtml = buildCeoEmail(config.getCeoName(), incompleteRecords, ceoIntro);
        send(mailSender, config.getFromEmail(), config.getCeoEmail(),
                "Course Completion Status – Master Report", ceoHtml);
    }

    // ------------------------------------------------------------------ //
    //  HTML email builders                                                 //
    // ------------------------------------------------------------------ //

    private String buildEmployeeEmail(IncompleteRecord record, String introParagraph) {
        return wrapHtml(
                "Dear " + record.employee().getName() + ",",
                introParagraph,
                """
                <table>
                  <thead>
                    <tr>
                      <th>Employee ID</th>
                      <th>Name</th>
                      <th>Department</th>
                      <th>Course Name</th>
                      <th>Course ID</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="color:#e53e3e;font-weight:bold;">Not Completed</td>
                    </tr>
                  </tbody>
                </table>
                """.formatted(
                        record.employee().getEmployeeId(),
                        record.employee().getName(),
                        record.employee().getDepartment(),
                        record.courseName(),
                        record.courseId()
                )
        );
    }

    private String buildMentorEmail(String mentorName, List<IncompleteRecord> records, String introParagraph) {
        StringBuilder rows = new StringBuilder();
        for (IncompleteRecord r : records) {
            rows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="color:#e53e3e;font-weight:bold;">Not Completed</td>
                    </tr>
                    """.formatted(
                    r.employee().getEmployeeId(),
                    r.employee().getName(),
                    r.employee().getEmail(),
                    r.courseName(),
                    r.courseId()
            ));
        }
        String table = """
                <table>
                  <thead>
                    <tr>
                      <th>Employee ID</th>
                      <th>Name</th>
                      <th>Email</th>
                      <th>Course Name</th>
                      <th>Course ID</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>%s</tbody>
                </table>
                """.formatted(rows.toString());

        return wrapHtml("Dear " + mentorName + ",", introParagraph, table);
    }

    private String buildCeoEmail(String ceoName, List<IncompleteRecord> records, String introParagraph) {
        StringBuilder rows = new StringBuilder();
        for (IncompleteRecord r : records) {
            rows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="color:#e53e3e;font-weight:bold;">Not Completed</td>
                    </tr>
                    """.formatted(
                    r.employee().getEmployeeId(),
                    r.employee().getName(),
                    r.employee().getEmail(),
                    r.employee().getDepartment(),
                    r.courseName(),
                    r.courseId()
            ));
        }
        String table = """
                <table>
                  <thead>
                    <tr>
                      <th>Employee ID</th>
                      <th>Name</th>
                      <th>Email</th>
                      <th>Department</th>
                      <th>Course Name</th>
                      <th>Course ID</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>%s</tbody>
                </table>
                """.formatted(rows.toString());

        return wrapHtml("Dear " + ceoName + ",", introParagraph, table);
    }

    /**
     * Wraps content in a clean, minimal HTML email layout.
     */
    private String wrapHtml(String greeting, String introParagraph, String tableHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    body { font-family: Arial, sans-serif; color: #333; background: #f9f9f9; padding: 20px; }
                    .container { background: #fff; border-radius: 8px; padding: 32px;
                                 max-width: 860px; margin: auto; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
                    h2 { color: #2c5282; margin-bottom: 4px; }
                    p  { line-height: 1.6; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 20px; }
                    th { background: #2c5282; color: #fff; padding: 10px 14px; text-align: left; }
                    td { padding: 9px 14px; border-bottom: 1px solid #e2e8f0; }
                    tr:nth-child(even) td { background: #f7fafc; }
                    .footer { margin-top: 28px; color: #718096; font-size: 0.9em; border-top: 1px solid #e2e8f0; padding-top: 16px; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <h2>Learning &amp; Development – Course Completion Notice</h2>
                    <p><b>%s</b></p>
                    <p>%s</p>
                    %s
                    <div class="footer">
                      <p>Regards,<br/><strong>L&amp;D Team</strong></p>
                      <p style="font-size:0.8em;color:#a0aec0;">This is an automated notification. Please do not reply directly to this email.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(greeting, introParagraph, tableHtml);
    }

    private String deriveMentorName(String mentorEmail) {
        if (mentorEmail == null || !mentorEmail.contains("@")) return mentorEmail;
        String local = mentorEmail.split("@")[0];
        return Arrays.stream(local.split("[._-]"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ------------------------------------------------------------------ //
    //  Mail sender factory                                                 //
    // ------------------------------------------------------------------ //

    private JavaMailSenderImpl buildMailSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.debug", "false");
        return sender;
    }

    private void send(JavaMailSenderImpl sender, String from, String to, String subject, String htmlBody)
            throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        sender.send(message);
    }
}
