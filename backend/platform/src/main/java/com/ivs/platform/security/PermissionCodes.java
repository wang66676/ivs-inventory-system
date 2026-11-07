package com.ivs.platform.security;

/** 进销存系统权限码：与《进销存系统落地方案 v1.0》第 10 章一致。 */
public final class PermissionCodes {
    public static final String IVS_READ = "IVS_READ";
    public static final String IVS_WRITE = "IVS_WRITE";

    /** 产品大项、商品、序列号、仓库、供应商、客户、价目表 */
    public static final String PRODUCT_MANAGE = "IVS_PRODUCT_MANAGE";
    /** 库存调整、盘点过账 */
    public static final String STOCK_ADJUST = "IVS_STOCK_ADJUST";
    /** 调拨 */
    public static final String STOCK_TRANSFER = "IVS_STOCK_TRANSFER";
    /** 低于最低限价的审批 */
    public static final String PRICE_APPROVE = "IVS_PRICE_APPROVE";

    /** 实销登记 */
    public static final String RETAIL_WRITE = "IVS_RETAIL_WRITE";
    /** 实销复核、确认、冲正 */
    public static final String RETAIL_VERIFY = "IVS_RETAIL_VERIFY";

    /** 政策录入与维护 */
    public static final String POLICY_WRITE = "IVS_POLICY_WRITE";
    /** 政策审批与发布 */
    public static final String POLICY_APPROVE = "IVS_POLICY_APPROVE";
    /** 订单勾选享受的政策（生成预享受项） */
    public static final String POLICY_SELECT = "IVS_POLICY_SELECT";
    /** 预享受项维护、后加政策补挂、取消 */
    public static final String PRE_ENTITLEMENT_MANAGE = "IVS_PRE_ENTITLEMENT_MANAGE";
    /** 实销后申请兑现 */
    public static final String ENTITLEMENT_APPLY = "IVS_ENTITLEMENT_APPLY";
    /** 政策额度池维护与调整 */
    public static final String SUBSIDY_POOL_MANAGE = "IVS_SUBSIDY_POOL_MANAGE";

    /** 申报单编制与提交 */
    public static final String CLAIM_SUBMIT = "IVS_CLAIM_SUBMIT";
    /** 厂家确认结果登记与差异处置 */
    public static final String CLAIM_CONFIRM = "IVS_CLAIM_CONFIRM";

    /** 返利资金池查询 */
    public static final String FUND_POOL_VIEW = "IVS_FUND_POOL_VIEW";
    /** 返利资金池人工调整 */
    public static final String FUND_POOL_ADJUST = "IVS_FUND_POOL_ADJUST";
    /** 返利抵扣车款（入库单确认时使用，无需审批） */
    public static final String FUND_OFFSET = "IVS_FUND_OFFSET";

    /** 开票 */
    public static final String INVOICE_WRITE = "IVS_INVOICE_WRITE";
    /** 红冲（销项与进项） */
    public static final String INVOICE_REDFLUSH = "IVS_INVOICE_REDFLUSH";
    /** 退款执行与人工兜底 */
    public static final String REFUND_MANAGE = "IVS_REFUND_MANAGE";

    /** 应收应付与返利报表 */
    public static final String FINANCE_READ = "IVS_FINANCE_READ";
    /** 二期：电池租赁 */
    public static final String LEASE_MANAGE = "IVS_LEASE_MANAGE";

    public static final String PROCESS_READ = "PROCESS_READ";
    public static final String USER_MANAGE = "USER_MANAGE";

    private PermissionCodes() {
    }
}
