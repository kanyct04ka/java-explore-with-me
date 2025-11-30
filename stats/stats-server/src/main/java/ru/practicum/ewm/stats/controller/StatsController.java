package ru.practicum.ewm.stats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.stats.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Staistic controller")
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
public class StatsController {

    private final StatsService statsService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointHitDto addHit(@RequestBody @Valid EndpointHitDto endpointHitDto) {
        log.info("Получен запрос на добавление события в статистику: {}", endpointHitDto);
        return statsService.addHit(endpointHitDto);
    }

    @Operation(summary = "Получить статистику", description = "full description of method")
    @GetMapping("/stats")
    public List<ViewStatsDto> getStats(
            @Parameter(description = "ID пользователя. Должен быть > 0", example = "42")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime end,
            @RequestParam(required = false)
            List<String> uris,
            @RequestParam(required = false, defaultValue = "false")
            Boolean unique
    ) {
        log.info("Запрос на выгрузку статистики за период с {} по {}", start, end);
        return statsService.getStats(start, end, uris, unique);
    }
}
