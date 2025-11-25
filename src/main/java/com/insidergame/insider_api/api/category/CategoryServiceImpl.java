package com.insidergame.insider_api.api.category;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.entity.CategoryEntity;
import com.insidergame.insider_api.repository.CategoryRepository;
import com.insidergame.insider_api.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public ApiResponse<List<CategoryEntity>> getAllCategoriesService() {
        List<CategoryEntity> res = categoryRepository.findAll();
        return new ApiResponse<>(true, "", res, HttpStatus.OK);
    }
}
