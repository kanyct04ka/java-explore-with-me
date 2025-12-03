package ru.practicum.ewm.service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.*;
import ru.practicum.ewm.exception.ConflictDataException;
import ru.practicum.ewm.exception.EntityNotFoundException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.*;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.UserRepository;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EventServiceImpl implements EventService {

    @Value("${app.name}")
    private String appName;
    private final LocalDateTime appCreationDate = LocalDateTime.parse("2025-11-30 12:00:00",
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    private final StatsClient statsClient;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    public EventFullDto addEvent(long userId, EventCreateDto eventCreateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанный пользователь ид=%s не найден", userId)));

        Category category = categoryRepository.findById(eventCreateDto.getCategory())
                .orElseThrow(() -> new ValidationException(String.format("Указан ид=%s несуществующей категории", eventCreateDto.getCategory())));

        Event event = eventMapper.toEvent(eventCreateDto);
        event.setInitiator(user);
        event.setCategory(category);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setConfirmedRequests(0);

        return eventMapper.toEventFullDto(eventRepository.save(event), null);
    }

    @Override
    public EventPublicFullDto getEvent(long eventId, String uri, String ip) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанное событие ид=%s не найдено", eventId)));

        addHit(uri, ip);
        return eventMapper.toEventPublicFullDto(event, getEventView(eventId));
    }

    @Override
    public List<EventShortDto> getEvents(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Boolean onlyAvailable,
            String sort,
            int from,
            int size,
            String uri,
            String ip
    ) {
        if (rangeStart != null && rangeEnd != null && !rangeStart.isBefore(rangeEnd)) {
            throw new ValidationException("Дата старта фильтра должна быть раньше даты окончания фильтра");
        }

        Pageable pageable;
        if (sort == null) {
            pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());
        } else if (sort.equalsIgnoreCase("VIEWS")) {
            pageable = PageRequest.of(from / size, size, Sort.by("views").ascending());
        } else if (sort.equalsIgnoreCase("EVENT_DATE")) {
            pageable = PageRequest.of(from / size, size, Sort.by("eventDate").ascending());
        } else {
            throw new ValidationException("Указан некорректный вариант сортировки");
        }

        addHit(uri, ip);

        rangeStart = rangeStart == null ? LocalDateTime.now() : rangeStart;

        List<Event> events = eventRepository.findEvents(
                text == null ? null : text.toLowerCase(),
                categories,
                paid,
                rangeStart,
                rangeEnd,
                onlyAvailable,
                EventState.PUBLISHED,
                pageable
        );
        return events.stream().map(eventMapper::toEventShortDto).toList();
    }

    @Override
    public EventFullDto getUserEvent(long userId, long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанный пользователь ид=%s не найден", userId)));

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанное событие ид=%s не найдено", eventId)));

        return eventMapper.toEventFullDto(event, getEventView(eventId));
    }

    @Override
    public List<EventShortDto> getUserEvents(long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанный пользователь ид=%s не найден", userId)));

        return eventRepository.findAllByInitiatorId(userId, pageable)
                .stream()
                .map(eventMapper::toEventShortDto)
                .toList();
    }

    @Override
    public List<EventFullDto> getAdminEvents(
            List<Long> users,
            List<String> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Pageable pageable
    ) {
        List<EventState> eventStates = null;
        if (states != null) {
            eventStates = states.stream()
                    .map(EventState::valueOf)
                    .toList();
        }

        List<Event> events = eventRepository.findAdminEvents(users, eventStates, categories, rangeStart, rangeEnd, pageable);

        Map<Long, Long> views = getEventsView(events.stream().map(Event::getId).toList());

        return events
                .stream()
                .map(event -> eventMapper.toEventFullDto(event, views.get(event.getId())))
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(long userId, long eventId, EventUpdateUserDto eventUpdateUserDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанный пользователь ид=%s не найден", userId)));

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанное событие ид=%s не найдено или принадлежит другому пользователю", eventId)));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictDataException("Изменить событие нельзя, т.к. оно опубликовано");
        }

        if (event.getEventDate().minusHours(2L).isBefore(LocalDateTime.now())) {
            throw new ConflictDataException("Событие можно менять не позднее чем за 2 часа до начала");
        }
        log.info("{} views {}", eventId, getEventView(eventId));
        return eventMapper.toEventFullDto(
                eventRepository.save(updateEventByUserData(event, eventUpdateUserDto)),
                getEventView(eventId)
        );
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(long eventId, EventUpdateAdminDto eventUpdateAdminDto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Указанное событие ид=%s не найдено", eventId)));

        return eventMapper.toEventFullDto(
                eventRepository.save(updateEventByAdminData(event, eventUpdateAdminDto)),
                getEventView(eventId)
        );
    }


    private Event updateEventByUserData(Event event, EventUpdateUserDto updateData) {

        if (updateData.getCategory() != null) {
            var category = categoryRepository.findById(updateData.getCategory())
                    .orElseThrow(() -> new ValidationException(String.format("Указан ид=%s несуществующей категории", updateData.getCategory())));
            event.setCategory(category);
        }

        var stateAction = updateData.getStateAction();
        if (stateAction != null) {

            if (stateAction == EventStateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
//              можно добавить очистку предыдущей резолюции модерации,
//              но лучше чтоб админ видел и мог быстро понять исправлены замечания или нет
            } else if (stateAction == EventStateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            } else {
                throw new ValidationException("Указан некорректный для данного метода stateAction");
            }
        }

        if (updateData.getEventDate() != null) {
            if (updateData.getEventDate().isBefore(LocalDateTime.now())) {
                throw new ValidationException("Дата события не может быть в прошлом");
            }
            event.setEventDate(updateData.getEventDate());
        }

        if (updateData.getTitle() != null) {
            event.setTitle(updateData.getTitle());
        }

        if (updateData.getAnnotation() != null) {
            event.setAnnotation(updateData.getAnnotation());
        }

        if (updateData.getDescription() != null) {
            event.setDescription(updateData.getDescription());
        }

        if (updateData.getParticipantLimit() != null) {
            event.setParticipantLimit(updateData.getParticipantLimit());
        }

        if (updateData.getLocation() != null) {
            event.setLocation(Location.builder()
                    .lon(updateData.getLocation().getLon())
                    .lat(updateData.getLocation().getLat())
                    .build()
            );
        }

        if (updateData.getPaid() != null) {
            event.setPaid(updateData.getPaid());
        }

        if (updateData.getRequestModeration() != null) {
            event.setRequestModeration(updateData.getRequestModeration());
        }

        return event;
    }

    private Event updateEventByAdminData(Event event, EventUpdateAdminDto updateData) {

        if (updateData.getCategory() != null) {
            var category = categoryRepository.findById(updateData.getCategory())
                    .orElseThrow(() -> new ValidationException("Указан ид несуществующей категории"));
            event.setCategory(category);
        }

        var state = event.getState();
        var updateStateAction = updateData.getStateAction();
        if (updateStateAction != null) {

            if (updateStateAction == EventStateAction.PUBLISH_EVENT) {
                if (state != EventState.PENDING) {
                    throw new ConflictDataException("Публиковать можно только события ожидающие публикации");
                }
                if (event.getEventDate().minusHours(1L).isBefore(LocalDateTime.now())) {
                    throw new ConflictDataException("Событие можно менять не позднее чем за час до начала");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
//                очищаем резолюцию модерации чтоб не смущала потом, раз опубликована то все замечания сняты
                event.setModerationResolution(null);

//            так как не проходят стандартные тесты, пришлось вынести возврат на доработку в отдельный StateAction
            } else if (updateStateAction == EventStateAction.RETURN_EVENT_FOR_MODIFY) {
                if (state == EventState.PUBLISHED) {
                    throw new ConflictDataException("вернуть опубликованное событие нельзя");
                }
                if (updateData.getModerationResolution() == null ||
                        updateData.getModerationResolution().isBlank()) {
                    throw new ValidationException("При возврате обязательно надо указывать причину");
                }
                event.setState(EventState.MODERATION_FAILED);
                event.setModerationResolution(updateData.getModerationResolution());

//                но по хорошему блок с обработкой StateAction == RETURN_EVENT_FOR_MODIFY не нужен
//                надо просто заменить блок обработки StateAction == REJECT_EVENT на тот что закомментирован ниже
//                с простой логикой - админ либо публикует либо возвращает пользователю с комментарием отказа
//                и дальше пользователь либо отменяет либо вносит правки и отправляет снова на проверку
//
//            } else if (updateStateAction == EventStateAction.REJECT_EVENT) {
//                if (state == EventState.PUBLISHED) {
//                    throw new ConflictDataException("Отменить опубликованное событие нельзя");
//                }
//                if (updateData.getModerationResolution() == null || updateData.getModerationResolution().isBlank()) {
//                    throw new ValidationException("При отмене обязательно надо указывать причину");
//                }
//                event.setState(EventState.MODERATION_FAILED);
//                event.setModerationResolution(updateData.getModerationResolution());

            } else if (updateStateAction == EventStateAction.REJECT_EVENT) {
                if (state == EventState.PUBLISHED) {
                    throw new ConflictDataException("Отменить опубликованное событие нельзя");
                }
                event.setState(EventState.CANCELED);

            } else {
                throw new ValidationException("Указан некорректный для данного метода stateAction");
            }
        }

        if (updateData.getTitle() != null) {
            event.setTitle(updateData.getTitle());
        }

        if (updateData.getAnnotation() != null) {
            event.setAnnotation(updateData.getAnnotation());
        }

        if (updateData.getDescription() != null) {
            event.setDescription(updateData.getDescription());
        }

        if (updateData.getEventDate() != null) {
            if (updateData.getEventDate().isBefore(LocalDateTime.now())) {
                throw new ValidationException("Дата события не может быть в прошлом");
            }
            event.setEventDate(updateData.getEventDate());
        }

        if (updateData.getParticipantLimit() != null) {
            event.setParticipantLimit(updateData.getParticipantLimit());
        }

        if (updateData.getLocation() != null) {
            event.setLocation(Location.builder()
                    .lon(updateData.getLocation().getLon())
                    .lat(updateData.getLocation().getLat())
                    .build()
            );
        }

        if (updateData.getPaid() != null) {
            event.setPaid(updateData.getPaid());
        }

        if (updateData.getRequestModeration() != null) {
            event.setRequestModeration(updateData.getRequestModeration());
        }

        return event;
    }

    private void addHit(String uri, String ip) {
        statsClient.addHit(
                EndpointHitDto.builder()
                        .uri(uri)
                        .app(appName)
                        .ip(ip)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    private long getEventView(long eventId) {
        List<String> uris = List.of("/events/" + eventId);

        List<ViewStatsDto> stats = statsClient.getStats(
                appCreationDate,
                LocalDateTime.now(),
                uris,
                true
        );

        return stats.isEmpty() ? 0L : stats.getFirst().getHits();
    }

    private Map<Long, Long> getEventsView(List<Long> ids) {
        List<String> uris = ids.stream()
                .map(id -> "/events/" + id)
                .toList();

        List<ViewStatsDto> stats = statsClient.getStats(
                appCreationDate,
                LocalDateTime.now(),
                uris,
                false
        );

        if (stats.isEmpty()) {
            return new HashMap<>();
        }

        return stats.stream()
                .map(viewStatsDto -> {
                    String eventIdStr = viewStatsDto.getUri().substring("/events/".length());
                    Long eventId = Long.parseLong(eventIdStr);
                    return Map.entry(eventId, viewStatsDto.getHits());
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    }
}
