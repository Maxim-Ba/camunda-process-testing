package com.example.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Делегат для отправки email подтверждения.
 * Выполняет HTTP POST запрос на эндпоинт отправки email.
 */
@Component("sendEmailDelegate")
public class SendEmailDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("=== Executing SendEmailDelegate ===");
        
        // Получаем переменные из контекста процесса
        String userEmail = (String) execution.getVariable("userEmail");
        String userName = (String) execution.getVariable("userName");
        String confirmationToken = (String) execution.getVariable("confirmationToken");
        
        logger.info("Sending email confirmation to: {}, userName={}, confirmationToken={}", 
                   userEmail, userName, confirmationToken);
        
        // Симулируем успешную отправку email
        // В реальном приложении здесь был бы HTTP вызов к email сервису
        execution.setVariable("emailSent", true);
        execution.setVariable("emailSentDate", java.time.LocalDateTime.now().toString());
        
        logger.info("Email sent successfully to: {}", userEmail);
    }
}
