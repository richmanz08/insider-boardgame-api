package com.insidergame.insider_api.service;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.entity.CategoryEntity;

import java.util.List;

public interface CategoryService {

    ApiResponse<List<CategoryEntity>> getAllCategoriesService();
}
