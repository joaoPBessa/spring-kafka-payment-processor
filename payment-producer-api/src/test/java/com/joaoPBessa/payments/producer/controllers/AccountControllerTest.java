package com.joaoPBessa.payments.producer.controllers;

import static org.mockito.ArgumentMatchers.argThat;
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
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.joaoPBessa.payments.producer.api.dto.request.CreateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.request.PageableAccountFilterRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.request.UpdateAccountRequestDTO;
import com.joaoPBessa.payments.producer.api.dto.response.AccountResponseDTO;
import com.joaoPBessa.payments.producer.domain.entities.Account;
import com.joaoPBessa.payments.producer.services.AccountService;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = AccountController.class, properties = {
    "spring.cloud.vault.enabled=false",
    "spring.cloud.bootstrap.enabled=false"
})
@DisplayName("AccountController")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("POST /api/v1/accounts/ -> Success: Should create a new account and return 201 Created")
    void shouldCreateAccountSuccessfully() throws Exception {
        var request = new CreateAccountRequestDTO("123456", "João Pedro");
        var expectedResponse = new AccountResponseDTO("123456", "João Pedro", true, LocalDateTime.now());

        // Account.id is a random UUID assigned by CreateAccountRequestDTO.toEntity() while
        // handling the request, so it can't be known upfront for an exact-equality match.
        ArgumentMatcher<Account> requestedAccount = account ->
                account.getNumber().equals("123456")
                        && account.getName().equals("João Pedro")
                        && account.getActive();

        when(accountService.save(argThat(requestedAccount))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/accounts/123456"))
                .andExpect(jsonPath("$.number").value("123456"))
                .andExpect(jsonPath("$.name").value("João Pedro"))
                .andExpect(jsonPath("$.active").value(true));

        verify(accountService).save(argThat(requestedAccount));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/ -> Validation: Should return 400 Bad Request when account number is under 4 characters")
    void shouldReturn400WhenAccountNumberIsTooShort() throws Exception {
        var request = new CreateAccountRequestDTO("123", "João Pedro");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /api/v1/accounts/ -> Validation: Should return 400 Bad Request when account number exceeds 8 characters")
    void shouldReturn400WhenAccountNumberIsTooLong() throws Exception {
        var request = new CreateAccountRequestDTO("123456789", "João Pedro");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /api/v1/accounts/ -> Validation: Should return 400 Bad Request when account number contains non-numeric characters")
    void shouldReturn400WhenAccountNumberContainsLetters() throws Exception {
        var request = new CreateAccountRequestDTO("1234A", "João Pedro");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /api/v1/accounts/ -> Validation: Should return 400 Bad Request when account name is blank")
    void shouldReturn400WhenAccountNameIsBlankOnCreation() throws Exception {
        var request = new CreateAccountRequestDTO("123456", "   ");

        mockMvc.perform(post("/api/v1/accounts/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("PATCH /api/v1/accounts/{accountNumber} -> Success: Should update account name and return 204 No Content")
    void shouldUpdateAccountSuccessfully() throws Exception {
        String accountNumber = "123456";
        var request = new UpdateAccountRequestDTO("João Pedro Bessa");

        doNothing().when(accountService).updateAccountName(accountNumber, "João Pedro Bessa");

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(accountService).updateAccountName(accountNumber, "João Pedro Bessa");
    }

    @Test
    @DisplayName("PATCH /api/v1/accounts/{accountNumber} -> Validation: Should return 400 Bad Request when update name is blank")
    void shouldReturn400WhenAccountNameIsBlankOnUpdate() throws Exception {
        String accountNumber = "123456";
        var request = new UpdateAccountRequestDTO("");

        mockMvc.perform(patch("/api/v1/accounts/{accountNumber}", accountNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("DELETE /api/v1/accounts/{accountNumber} -> Success: Should remove account and return 204 No Content")
    void shouldDeleteAccountSuccessfully() throws Exception {
        String accountNumber = "123456";

        doNothing().when(accountService).deleteAccount(accountNumber);

        mockMvc.perform(delete("/api/v1/accounts/{accountNumber}", accountNumber))
                .andExpect(status().isNoContent());

        verify(accountService).deleteAccount(accountNumber);
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountNumber} -> Success: Should fetch account details and return 200 OK")
    void shouldReturnAccountDetailsWhenFound() throws Exception {
        String accountNumber = "123456";
        var expectedResponse = new AccountResponseDTO(accountNumber, "João Pedro", true, LocalDateTime.now());

        when(accountService.findByNumber(accountNumber)).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/v1/accounts/{accountNumber}", accountNumber)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(accountNumber))
                .andExpect(jsonPath("$.name").value("João Pedro"));

        verify(accountService).findByNumber(accountNumber);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Success: Should bind snake_case filter parameters and return a page of accounts")
    void shouldReturnPaginatedAccountsWithValidFilters() throws Exception {
        var accountDto = new AccountResponseDTO("123456", "João Pedro", true, LocalDateTime.now());
        var pageResponse = new PageImpl<>(List.of(accountDto));
        var expectedFilter = new PageableAccountFilterRequestDTO("12345", "João", true, 0, 10);

        when(accountService.findAccountsByFilter(expectedFilter)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12345")
                .param("account_name", "João")
                .param("active", "true")
                .param("page", "0")
                .param("size", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value("123456"))
                .andExpect(jsonPath("$.content[0].name").value("João Pedro"));

        verify(accountService).findAccountsByFilter(expectedFilter);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Validation: Should return 400 Bad Request when required pagination parameters are missing")
    void shouldReturn400WhenPaginationParametersAreMissing() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12345"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Validation: Should return 400 Bad Request when page index is negative")
    void shouldReturn400WhenPageIndexIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("page", "-1")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Validation: Should return 400 Bad Request when page size is less than 1")
    void shouldReturn400WhenPageSizeIsLessThanOne() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("page", "0")
                .param("size", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Validation: Should return 400 Bad Request when account number filter is under 3 characters")
    void shouldReturn400WhenAccountNumberFilterIsTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_number", "12")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("GET /api/v1/accounts -> Validation: Should return 400 Bad Request when account name filter is under 3 characters")
    void shouldReturn400WhenAccountNameFilterIsTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .param("account_name", "Jo")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}
