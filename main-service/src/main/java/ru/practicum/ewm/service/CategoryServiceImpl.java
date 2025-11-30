package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.CategoryDataDto;
import ru.practicum.ewm.dto.CategoryDto;
import ru.practicum.ewm.exception.ConflictDataException;
import ru.practicum.ewm.exception.EntityNotFoundException;
import ru.practicum.ewm.mapper.CategoryMapper;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.repository.CategoryRepository;

import java.util.List;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryDto addCategory(CategoryDataDto categoryDataDto) {
        if (categoryRepository.findByName(categoryDataDto.getName()).isPresent()) {
            throw new ConflictDataException("Категория с таким именем существует");
        }

        Category category = categoryMapper.toCategory(categoryDataDto);
        return categoryMapper.toCategoryDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void removeCategory(int catId) {
//        TODO: добавить проверку на наличие событий, привязанных к категории

        categoryRepository.deleteById(catId);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(int catId, CategoryDataDto categoryDataDto) {
//        тут выполняется 2 запроса к базе на одну операцию, для сокращения можно сделать 1 запрос,
//        который бы вернул массив элементов с записями с ид и со схожим наименованием, а потом их перебирать и анализировать
//        с учетом редкости изменения справочника категорий как выделенного бизнес процесса
//        решил оставить 2 запроса - реальной нагрузки на базу создавать не должно

        Category category = categoryRepository.findById(catId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Категория с указанным ид=%s не найдена", catId))
        );

        if (categoryRepository.findByName(categoryDataDto.getName()).isPresent()) {
            throw new ConflictDataException("Категория с таким именем существует");
        }

        category.setName(categoryDataDto.getName());
        return categoryMapper.toCategoryDto(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDto> getCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .stream()
                .map(categoryMapper::toCategoryDto)
                .toList();
    }

    @Override
    public CategoryDto getCategory(int catId) {
        Category category = categoryRepository.findById(catId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Категория с указанным ид=%s не найдена", catId))
        );

        return categoryMapper.toCategoryDto(category);
    }

}
