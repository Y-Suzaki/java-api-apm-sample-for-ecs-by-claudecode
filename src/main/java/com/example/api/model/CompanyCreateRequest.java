package com.example.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String address
) {}