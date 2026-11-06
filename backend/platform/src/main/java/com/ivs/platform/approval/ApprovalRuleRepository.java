package com.ivs.platform.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, String> {
    List<ApprovalRule> findAllByOrderBySortOrderAscRuleCodeAsc();
}
