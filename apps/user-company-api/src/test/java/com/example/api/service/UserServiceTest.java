package com.example.api.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.example.api.exception.UserAlreadyExistsException;
import com.example.api.exception.UserNotFoundException;
import com.example.api.model.UserCreateRequest;
import com.example.api.model.UserItem;
import com.example.api.model.UserResponse;
import com.example.api.model.UserUpdateRequest;
import com.example.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void create_savesItemWithGeneratedTimestampsAndReturnsResponse() {
        UserCreateRequest request = new UserCreateRequest("taro@example.com", "Taro");
        ArgumentCaptor<UserItem> captor = ArgumentCaptor.forClass(UserItem.class);

        UserResponse response = userService.create(request);

        verify(userRepository).save(captor.capture());
        UserItem saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("taro@example.com");
        assertThat(saved.getName()).isEqualTo("Taro");
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
        // ISO-8601 として解釈できること(フォーマット崩れがないこと)を確認する
        assertThat(Instant.parse(saved.getCreatedAt())).isNotNull();

        assertThat(response.email()).isEqualTo("taro@example.com");
        assertThat(response.name()).isEqualTo("Taro");
        assertThat(response.createdAt()).isEqualTo(response.updatedAt());
    }

    @Test
    void create_whenEmailAlreadyExists_throwsUserAlreadyExistsException() {
        UserCreateRequest request = new UserCreateRequest("taro@example.com", "Taro");
        doThrow(new ConditionalCheckFailedException("dup"))
                .when(userRepository).save(any());

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("taro@example.com");
    }

    @Test
    void list_returnsMappedResponses() {
        UserItem item = itemOf("taro@example.com", "Taro");
        when(userRepository.findAll(10)).thenReturn(List.of(item));

        List<UserResponse> result = userService.list(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("taro@example.com");
    }

    @Test
    void get_whenFound_returnsMappedResponse() {
        UserItem item = itemOf("taro@example.com", "Taro");
        when(userRepository.findByEmail("taro@example.com")).thenReturn(Optional.of(item));

        UserResponse response = userService.get("taro@example.com");

        assertThat(response.email()).isEqualTo("taro@example.com");
        assertThat(response.name()).isEqualTo("Taro");
    }

    @Test
    void get_whenNotFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    @Test
    void update_whenFound_returnsUpdatedResponse() {
        UserItem updated = itemOf("taro@example.com", "Jiro");
        when(userRepository.update(eq("taro@example.com"), eq("Jiro"), anyString()))
                .thenReturn(updated);

        UserResponse response = userService.update("taro@example.com", new UserUpdateRequest("Jiro"));

        assertThat(response.name()).isEqualTo("Jiro");
    }

    @Test
    void update_whenNotFound_throwsUserNotFoundException() {
        when(userRepository.update(eq("missing@example.com"), anyString(), anyString()))
                .thenThrow(new ConditionalCheckFailedException("not found"));

        assertThatThrownBy(() -> userService.update("missing@example.com", new UserUpdateRequest("Jiro")))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }

    private static UserItem itemOf(String email, String name) {
        UserItem item = new UserItem();
        item.setEmail(email);
        item.setName(name);
        String now = Instant.now().toString();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }
}