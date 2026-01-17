package com.example.demo.process;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.example.demo.delegate.SendEmailDelegate;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Тест процесса регистрации пользователя с реальным выполнением CallActivity.
 * 
 * Демонстрирует:
 * - HTTP Connector (create-registration-task) - без делегата
 * - Java Delegate (send-email-confirmation) - с моком
 * - CallActivity с реальным выполнением подпроцесса
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Deployment(resources = {
    "bpmn/user-registration.bpmn",
    "bpmn/chose-next-process.bpmn"
})
public class UserRegistrationProcessWithSubprocessTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    private SendEmailDelegate sendEmailDelegate;

    @BeforeEach
    public void setUp() {
        // Создаем мок только для SendEmailDelegate
        // CreateRegistrationTask использует HTTP connector напрямую (не делегат)
        sendEmailDelegate = Mockito.mock(SendEmailDelegate.class);
        
        // Регистрируем мок делегата в Camunda
        Mocks.register("sendEmailDelegate", sendEmailDelegate);
    }
    
    /**
     * Вспомогательный метод для запуска процесса с SERVICE_API переменной.
     */
    private ProcessInstance startProcessWithServiceApi(String userName, String userEmail) {
        return runtimeService.startProcessInstanceByKey(
            "user-registration-process",
            withVariables(
                "userName", userName,
                "userEmail", userEmail,
                "SERVICE_API", "http://mock-service:9999"
            )
        );
    }

    /**
     * Тест полного прохождения процесса регистрации с выполнением подпроцесса.
     * CallActivity реально выполняет процесс chose-next-process.
     */
    @Test
    public void testCompleteFlowWithSubprocess() throws Exception {
        // Given: Подготовка входных данных
        String testUserName = "Test User With Subprocess";
        String testUserEmail = "test.subprocess@example.com";

        // When: Запуск процесса с входными переменными
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Then: Проверяем, что процесс запущен и остановился на ReceiveTask
        assertThat(processInstance).isNotNull();
        assertThat(processInstance).isWaitingAt("Activity_0g1mra7");

        // When: Отправляем сообщение для продолжения процесса
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .correlate();

        // Then: Процесс должен завершиться, так как подпроцесс выполнится полностью
        assertThat(processInstance).isEnded();

        // Проверяем, что переменные из подпроцесса были установлены
        assertThat(
            (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("nextProcess")
                .singleResult()
                .getValue()
        ).isEqualTo("onboarding-process");
        
        assertThat(
            (Boolean) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("processChosen")
                .singleResult()
                .getValue()
        ).isTrue();
    }

    /**
     * Тест проверки передачи переменных в подпроцесс и обратно.
     */
    @Test
    public void testVariablesPropagationToSubprocess() throws Exception {
        // Given
        String testUserName = "Variable Test User";
        String testUserEmail = "variable.test@example.com";

        // When
        ProcessInstance processInstance = startProcessWithServiceApi(testUserName, testUserEmail);

        // Отправляем сообщение
        runtimeService.createMessageCorrelation("email_confirmed_message")
            .processInstanceId(processInstance.getId())
            .setVariable("emailConfirmed", true)
            .correlate();

        // Then: Процесс завершен
        assertThat(processInstance).isEnded();

        // Проверяем, что все переменные корректно прошли через весь процесс
        assertThat(
            (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("userName")
                .singleResult()
                .getValue()
        ).isEqualTo(testUserName);

        assertThat(
            (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("userEmail")
                .singleResult()
                .getValue()
        ).isEqualTo(testUserEmail);

        // Переменные, установленные в подпроцессе, должны быть доступны в родительском процессе
        assertThat(
            (String) historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstance.getId())
                .variableName("nextProcess")
                .singleResult()
                .getValue()
        ).isNotNull();
    }
}
