package com.insidergame.insider_api.repository;

import com.insidergame.insider_api.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity , Long> {
}
