package com.ivs.platform.license;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, String> {
}
