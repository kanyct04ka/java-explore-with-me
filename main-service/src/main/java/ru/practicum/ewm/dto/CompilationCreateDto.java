package ru.practicum.ewm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationCreateDto {

    @NotBlank
    @Size(min = 1, max = 50)
    private String title;

    private Set<Long> events;

    @Builder.Default
    private Boolean pinned = false;

}
