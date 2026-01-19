package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SettingRepository extends JpaRepository<SystemSetting, String> {
}


