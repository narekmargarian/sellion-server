package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.CompanySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CompanySettingRepository extends JpaRepository<CompanySetting, String> {
}


