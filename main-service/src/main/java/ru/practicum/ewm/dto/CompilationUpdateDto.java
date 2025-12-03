package ru.practicum.ewm.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CompilationUpdateDto {

    @Size(max = 50)
    private String title;

    private Set<Long> events;
    private Boolean pinned;

}
