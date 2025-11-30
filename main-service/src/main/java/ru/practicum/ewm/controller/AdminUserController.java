package ru.practicum.ewm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.UserCreateDto;
import ru.practicum.ewm.service.UserService;

import java.util.List;

@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@Tag(name = "Admin: Пользователи")
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Добавление нового пользователя")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void registerUser(
            @RequestBody @Valid UserCreateDto userCreateDto
    ) {
        log.info("Получен запрос от админа на добавление нового пользователя: {}", userCreateDto);
        userService.addUser(userCreateDto);
    }

    @Operation(summary = "Удаление пользователя")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable long userId
    ) {
        log.info("Получен запрос от админа на удаление пользователя с ид = {}", userId);
        userService.removeUser(userId);
    }

    @Operation(summary = "Получение информации о пользователях")
    @GetMapping
    public void getUsers(
            @RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Получен запрос от админа на выборку пользователей");
        userService.getUsers(ids, PageRequest.of(from / size, size));
    }

}
