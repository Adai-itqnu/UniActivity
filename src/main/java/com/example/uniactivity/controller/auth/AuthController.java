package com.example.uniactivity.controller.auth;

import com.example.uniactivity.dto.auth.UserRegistrationDto;
import com.example.uniactivity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserRegistrationDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUserAccount(@ModelAttribute("user") @Valid UserRegistrationDto registrationDto,
                                      BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "auth/register";
        }

        if (!registrationDto.getPassword().equals(registrationDto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "error.user", "Mật khẩu nhập lại không khớp");
            return "auth/register";
        }

        if (!registrationDto.isTermsAccepted()) {
            result.rejectValue("termsAccepted", "error.user", "Bạn phải chấp nhận các điều khoản");
            return "auth/register";
        }

        try {
            userService.registerUser(registrationDto);
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }

        return "redirect:/login?success";
    }
    
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
