package com.insidergame.insider_api.api.category;

import com.insidergame.insider_api.common.ApiResponse;
import com.insidergame.insider_api.entity.CategoryEntity;
import com.insidergame.insider_api.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/category")
public class CategoryController {

    CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CategoryEntity>>> getAllCategories() {
        ApiResponse<List<CategoryEntity>> response = categoryService.getAllCategoriesService();
        return ResponseEntity.status(response.getStatus()).body(response);
    }

}
