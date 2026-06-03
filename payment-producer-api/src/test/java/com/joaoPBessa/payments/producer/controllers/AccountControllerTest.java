package com.joaoPBessa.payments.producer.controllers;

import static org.mockito.ArgumentMatchers.eq;
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
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joaoPBessa.payments.producer.api.dto.request.CreateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.request.UpdateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.services.AccountService;

@WebMvcTest(controllers = AccountController.class)
@DisplayName("Account Controller Unit Tests")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @MockitoBean
    private AccountService accountService;

    // =========================================================================
    // POST /api/v1/accounts/
    // =========================================================================

    @Test
    @DisplayName("POST /accounts/ -> Should create account successfully and return 201 Created with Location header")
    void shouldCreateAccountSuccessfully() throws Exception {
        var request = new CreateAccountRequestDTO("123456", "John Doe");
        
        UUID fixedUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        var expectedAccountInput = Account.builder()
                .id(fixedUuid)
                .number("123456")
                .name("John Doe")
                .active(Boolean.TRUE)
                .build();

        var response = new AccountResponseDTO("123456", "John Doe", true, LocalDateTime.now());

        try (MockedStatic<UUID> mockedUuid = Mockito.mockStatic(UUID.class)) {
            
            mockedUuid.when(UUID::randomUUID).thenReturn(fixedUuid);
            when(accountService.save(eq(expectedAccountInput))).thenReturn(response);

            mockMvc.perform(post("/api/v1/accounts/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/v1/accounts/123456"))
                    .andExpect(jsonPath("$.number").value("123456"))
                    .andExpect(jsonPath("$.name").value("John Doe"));
        } 
    }

    @Test
    @DisplayName("POST /accounts/ -> Should return 400 Bad Request when Account Number is invalid")
    void shouldReturn400WhenAccountNumberIsInvalid() throws Exception {
        var invalidRequest = new CreateAccountRequestDTO("abc", "John Doe");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /accounts/ -> Should return 400 Bad Request when Account Name is blank")
    void shouldReturn400WhenAccountNameIsBlank() throws Exception {
        var invalidRequest = new CreateAccountRequestDTO("123456", "    ");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // =========================================================================
	//  PATCH /api/v1/accounts/{accountNumber}
    // =========================================================================

    @Test
    @DisplayName("PATCH /accounts/{id} -> Should update account name successfully and return 204 No Content")
    void shouldUpdateAccountNameSuccessfully() throws Exception {
        String accountNumber = "123456";
        var request = new UpdateAccountRequestDTO("John Doe Updated");

        doNothing().when(accountService).updateAccountName(eq(accountNumber), eq("John Doe Updated"));

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountService).updateAccountName(accountNumber, "John Doe Updated");
    }

    @Test
    @DisplayName("PATCH /accounts/{id} -> Should return 400 Bad Request when Update Account Name is blank")
    void shouldReturn400WhenUpdateAccountNameIsBlank() throws Exception {
        String accountNumber = "123456";
        var invalidRequest = new UpdateAccountRequestDTO(""); 

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // =========================================================================
    //  DELETE /api/v1/accounts/{accountNumber}
    // =========================================================================

    @Test
    @DisplayName("DELETE /accounts/{id} -> Should delete account successfully and return 204 No Content")
    void shouldDeleteAccountSuccessfully() throws Exception {
        String accountNumber = "123456";
        
        // Mantém a validação estrita do valor numérico real, garantindo robustez no CI
        doNothing().when(accountService).deleteAccount(accountNumber);

        mockMvc.perform(delete("/api/v1/accounts/{accountNumber}", accountNumber))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount(accountNumber);
    }

    // =========================================================================
    //  GET /api/v1/accounts/{accountNumber}
    // =========================================================================

    @Test
    @DisplayName("GET /accounts/{id} -> Should return account data when found and return 200 OK")
    void shouldReturnAccountWhenFound() throws Exception {
        String accountNumber = "123456";
        var response = new AccountResponseDTO(accountNumber, "John Doe", true, LocalDateTime.now());

        // Mantém a segurança de validar o mapeamento perfeito do ID estrito recebido na URL
        when(accountService.findByNumber(accountNumber)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{accountNumber}", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("123456"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    // =========================================================================
    //  GET /api/v1/accounts (Pageable)
    // =========================================================================

    @Test
    @DisplayName("GET /accounts -> Should return paginated accounts based on filter and return 200 OK")
    void shouldReturnPaginatedAccounts() throws Exception {
        var responseDto = new AccountResponseDTO("123456", "John Doe", true, LocalDateTime.now());
        var pageResponse = new PageImpl<>(List.of(responseDto));

        var expectedFilterInput = new PageableAccountFilterRequestDTO(
                "123456",
                "John Doe",
                true,
                0,
                10
        );

        when(accountService.findAccountsByFilter(eq(expectedFilterInput))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "123456")
                .param("account_name", "John Doe")
                .param("active", "true")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value("123456"))
                .andExpect(jsonPath("$.content[0].name").value("John Doe"));
        
        verify(accountService).findAccountsByFilter(expectedFilterInput);
    }

    @Test
    @DisplayName("GET /accounts -> Should return 400 Bad Request when pagination parameters are missing or invalid")
    void shouldReturn400WhenPaginationParametersAreInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("page", "-1")
                .param("size", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /accounts -> Should return 400 Bad Request when filter parameters are too short")
    void shouldReturn400WhenFiltersAreTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12")
                .param("account_name", "Jo")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}