package com.baibyname.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "{form.email.required}")
    @Email(message = "{form.email.invalid}")
    @Size(max = 254, message = "{form.email.max}")
    private String email;

    @NotBlank(message = "{form.password.required}")
    @Size(min = 8, max = 100, message = "{form.password.size}")
    private String password;

    @NotBlank(message = "{form.password.confirm.required}")
    private String confirmPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
