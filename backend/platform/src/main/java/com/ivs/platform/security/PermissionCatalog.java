package com.ivs.platform.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 可分配权限目录：角色权限页与权限校验共用同一份清单。 */
public final class PermissionCatalog {

    /** 权限分组 → 该组内的权限（顺序即展示顺序）。 */
    private static final Map<String, List<Item>> GROUPS = new LinkedHashMap<>();

    public record Item(String code, String label) {
    }

    public static final List<String> ROLE_CODES = List.of("ADMIN", "SALES", "WAREHOUSE", "FINANCE", "POLICY_ADMIN");

    public static final Map<String, String> ROLE_NAMES = Map.of(
            "ADMIN", "系统管理员",
            "SALES", "销售顾问",
            "WAREHOUSE", "库管",
            "FINANCE", "财务",
            "POLICY_ADMIN", "政策管理员"
    );

    static {
        GROUPS.put("进销存基础", List.of(
                new Item(PermissionCodes.IVS_READ, "查看商品/库存/采购/销售/实销/政策"),
                new Item(PermissionCodes.IVS_WRITE, "新增与修改进销存单据")
        ));
        GROUPS.put("主数据", List.of(
                new Item(PermissionCodes.PRODUCT_MANAGE, "产品大项、商品、序列号、仓库、供应商、客户、价目表")
        ));
        GROUPS.put("库存", List.of(
                new Item(PermissionCodes.STOCK_ADJUST, "库存调整与盘点过账"),
                new Item(PermissionCodes.STOCK_TRANSFER, "库存调拨")
        ));
        GROUPS.put("价格", List.of(
                new Item(PermissionCodes.PRICE_APPROVE, "低于最低限价的成交审批")
        ));
        GROUPS.put("实销", List.of(
                new Item(PermissionCodes.RETAIL_WRITE, "实销登记（车辆使用人、手机号、终端发票开票时间、车牌号）"),
                new Item(PermissionCodes.RETAIL_VERIFY, "实销复核、确认与冲正")
        ));
        GROUPS.put("政策与返利", List.of(
                new Item(PermissionCodes.POLICY_WRITE, "政策录入与维护（名目、车型、金额、限制维度、参考时间）"),
                new Item(PermissionCodes.POLICY_APPROVE, "政策审批与发布"),
                new Item(PermissionCodes.SUBSIDY_POOL_MANAGE, "政策额度池维护与调整"),
                new Item(PermissionCodes.POLICY_SELECT, "订单勾选享受的政策（生成预享受项）"),
                new Item(PermissionCodes.PRE_ENTITLEMENT_MANAGE, "预享受项维护、后加政策补挂、取消"),
                new Item(PermissionCodes.ENTITLEMENT_APPLY, "实销后申请兑现")
        ));
        GROUPS.put("申报与厂家对账", List.of(
                new Item(PermissionCodes.CLAIM_SUBMIT, "申报单编制与提交"),
                new Item(PermissionCodes.CLAIM_CONFIRM, "厂家确认结果登记与差异处置")
        ));
        GROUPS.put("返利资金池", List.of(
                new Item(PermissionCodes.FUND_POOL_VIEW, "返利资金池查询（总余额与按厂家子余额）"),
                new Item(PermissionCodes.FUND_POOL_ADJUST, "返利资金池人工调整"),
                new Item(PermissionCodes.FUND_OFFSET, "返利抵扣车款（入库单确认时使用）")
        ));
        GROUPS.put("票据", List.of(
                new Item(PermissionCodes.INVOICE_WRITE, "开票"),
                new Item(PermissionCodes.INVOICE_REDFLUSH, "红冲（销项与进项）"),
                new Item(PermissionCodes.REFUND_MANAGE, "退款执行、重试与人工兜底")
        ));
        GROUPS.put("报表", List.of(
                new Item(PermissionCodes.FINANCE_READ, "应收应付、返利与政策报表"),
                new Item(PermissionCodes.PROCESS_READ, "业务流程与经营看板")
        ));
        GROUPS.put("二期能力", List.of(
                new Item(PermissionCodes.LEASE_MANAGE, "电池租赁、换电与资产（二期）")
        ));
        GROUPS.put("系统管理", List.of(
                new Item(PermissionCodes.USER_MANAGE, "用户、角色权限、组织与审计日志")
        ));
    }

    private PermissionCatalog() {
    }

    public static Map<String, List<Item>> groups() {
        return GROUPS;
    }

    public static Set<String> allCodes() {
        return GROUPS.values().stream()
                .flatMap(List::stream)
                .map(Item::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isAssignable(String roleCode) {
        return ROLE_CODES.contains(roleCode);
    }
}
