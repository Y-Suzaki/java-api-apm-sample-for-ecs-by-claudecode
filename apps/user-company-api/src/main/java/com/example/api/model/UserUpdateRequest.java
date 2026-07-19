package com.example.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/{email} のリクエストボディ。
 *
 * <p>更新可能なフィールドは name のみ。
 * email はパスパラメータで指定するため、このリクエストには含まない。
 */
public record UserUpdateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be 100 characters or less")
        String name
) {}