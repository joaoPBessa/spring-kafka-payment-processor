package com.joaoPBessa.payments.producer.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joaoPBessa.payments.producer.api.dto.request.CreateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.request.UpdateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.services.AccountService;

@WebMvcTest(controllers = AccountController.class, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@ImportAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@DisplayName("Testes Unitários do Painel de Contas (AccountController)")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Configuração do ObjectMapper para corresponder à estratégia Snake Case e tipos de data do Java 8+
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @MockitoBean
    private AccountService accountService;

    // =========================================================================
    // SEÇÃO: CRIAÇÃO DE CONTA (POST /api/v1/accounts/)
    // =========================================================================

    @Test
    @DisplayName("POST / - Deve criar uma conta com sucesso e retornar 201 Created")
    void shouldCreateAccountSuccessfully() throws Exception {
        var request = new CreateAccountRequestDTO("123456", "João Silva");
        var expectedResponse = new AccountResponseDTO("123456", "João Silva", true, LocalDateTime.now());

        // Stub mapeando o comportamento do mapeamento interno 'request.toEntity()'
        when(accountService.save(any(Account.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/accounts/123456"))
                .andExpect(jsonPath("$.number").value("123456"))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.active").value(true));

        verify(accountService).save(any(Account.class));
    }

    @Test
    @DisplayName("POST / - Deve retornar 400 Bad Request quando o número da conta for menor que 4 dígitos")
    void shouldReturn400WhenAccountNumberIsTooShort() throws Exception {
        var request = new CreateAccountRequestDTO("123", "João Silva");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST / - Deve retornar 400 Bad Request quando o número da conta for maior que 8 dígitos")
    void shouldReturn400WhenAccountNumberIsTooLong() throws Exception {
        var request = new CreateAccountRequestDTO("123456789", "João Silva");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST / - Deve retornar 400 Bad Request quando o número da conta possuir caracteres não numéricos")
    void shouldReturn400WhenAccountNumberContainsLetters() throws Exception {
        var request = new CreateAccountRequestDTO("1234A", "João Silva");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST / - Deve retornar 400 Bad Request quando o nome da conta estiver em branco")
    void shouldReturn400WhenAccountNameIsBlankOnCreation() throws Exception {
        var request = new CreateAccountRequestDTO("123456", "   ");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // =========================================================================
    // SEÇÃO: ATUALIZAÇÃO DE CONTA (PATCH /api/v1/accounts/{accountNumber})
    // =========================================================================

    @Test
    @DisplayName("PATCH /{number} - Deve atualizar o nome da conta com sucesso e retornar 204 No Content")
    void shouldUpdateAccountSuccessfully() throws Exception {
        String accountNumber = "123456";
        var request = new UpdateAccountRequestDTO("João Silva Alterado");

        doNothing().when(accountService).updateAccountName(accountNumber, "João Silva Alterado");

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountService).updateAccountName(accountNumber, "João Silva Alterado");
    }

    @Test
    @DisplayName("PATCH /{number} - Deve retornar 400 Bad Request quando o nome para atualização estiver em branco")
    void shouldReturn400WhenAccountNameIsBlankOnUpdate() throws Exception {
        String accountNumber = "123456";
        var request = new UpdateAccountRequestDTO("");

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // =========================================================================
    // SEÇÃO: EXCLUSÃO DE CONTA (DELETE /api/v1/accounts/{accountNumber})
    // =========================================================================

    @Test
    @DisplayName("DELETE /{number} - Deve excluir uma conta com sucesso e retornar 204 No Content")
    void shouldDeleteAccountSuccessfully() throws Exception {
        String accountNumber = "123456";

        doNothing().when(accountService).deleteAccount(accountNumber);

        mockMvc.perform(delete("/api/v1/accounts/{accountNumber}", accountNumber))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount(accountNumber);
    }

    // =========================================================================
    // SEÇÃO: BUSCA POR NÚMERO (GET /api/v1/accounts/{accountNumber})
    // =========================================================================

    @Test
    @DisplayName("GET /{number} - Deve retornar os dados da conta encontrada com status 200 OK")
    void shouldReturnAccountDetailsWhenFound() throws Exception {
        String accountNumber = "123456";
        var expectedResponse = new AccountResponseDTO(accountNumber, "João Silva", true, LocalDateTime.now());

        when(accountService.findByNumber(accountNumber)).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/v1/accounts/{accountNumber}", accountNumber)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(accountNumber))
                .andExpect(jsonPath("$.name").value("João Silva"));

        verify(accountService).findByNumber(accountNumber);
    }

    // =========================================================================
    // SEÇÃO: CONSULTA PAGINADA E FILTROS (GET /api/v1/accounts)
    // =========================================================================

    @Test
    @DisplayName("GET / - Deve retornar uma página de contas com sucesso utilizando parâmetros de filtro válidos")
    void shouldReturnPaginatedAccountsWithValidFilters() throws Exception {
        var accountDto = new AccountResponseDTO("123456", "João Silva", true, LocalDateTime.now());
        var pageResponse = new PageImpl<>(List.of(accountDto));

        when(accountService.findAccountsByFilter(any())).thenReturn(pageResponse);

        // Mapeamento usando parâmetros com o padrão @BindParam (Snake Case) definidos no Record
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12345")
                .param("account_name", "João")
                .param("active", "true")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value("123456"))
                .andExpect(jsonPath("$.content[0].name").value("João Silva"));

        verify(accountService).findAccountsByFilter(any());
    }

    @Test
    @DisplayName("GET / - Deve retornar 400 Bad Request quando os parâmetros obrigatórios de paginação estiverem ausentes")
    void shouldReturn400WhenPaginationParametersAreMissing() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12345"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET / - Deve retornar 400 Bad Request quando o índice de página (page) for menor que 0")
    void shouldReturn400WhenPageIndexIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("page", "-1")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET / - Deve retornar 400 Bad Request quando o tamanho da página (size) for menor que 1")
    void shouldReturn400WhenPageSizeIsLessThanOne() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("page", "0")
                .param("size", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET / - Deve retornar 400 Bad Request quando o filtro de número da conta possuir menos de 3 caracteres")
    void shouldReturn400WhenAccountNumberFilterIsTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET / - Deve retornar 400 Bad Request quando o filtro de nome da conta possuir menos de 3 caracteres")
    void shouldReturn400WhenAccountNameFilterIsTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_name", "Jo")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}