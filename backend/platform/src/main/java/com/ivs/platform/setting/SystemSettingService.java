package com.ivs.platform.setting;

import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 全局参数：仅管理员可查看与修改。 */
@Service
public class SystemSettingService {
    private final SystemSettingRepository repository;
    private final CurrentActorProvider actors;

    public SystemSettingService(SystemSettingRepository repository, CurrentActorProvider actors) {
        this.repository = repository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<SettingView> list() {
        return repository.findAllByOrderBySettingGroupAscSortOrderAsc().stream().map(SettingView::from).toList();
    }

    @Transactional
    public SettingView update(String key, String value) {
        SystemSetting setting = repository.findById(key)
                .orElseThrow(() -> new NotFoundException("全局参数", UUID.nameUUIDFromBytes(key.getBytes())));
        String normalized = value == null ? "" : value.trim();
        validate(setting.getValueType(), normalized);
        setting.update(normalized, actors.current().map(a -> a.userId()).orElse(null));
        return SettingView.from(repository.save(setting));
    }

    private void validate(String type, String value) {
        if (value.isBlank()) throw new ConflictException("SETTING_VALUE_REQUIRED", "参数值不能为空");
        try {
            if ("NUMBER".equals(type)) {
                if (new BigDecimal(value).signum() < 0) throw new NumberFormatException();
            } else if ("PERCENT".equals(type)) {
                BigDecimal percent = new BigDecimal(value);
                if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new ConflictException("SETTING_VALUE_INVALID", "百分比必须在 0-100 之间");
                }
            } else if ("BOOLEAN".equals(type)) {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new ConflictException("SETTING_VALUE_INVALID", "布尔值只能为 true / false");
                }
            }
        } catch (NumberFormatException exception) {
            throw new ConflictException("SETTING_VALUE_INVALID", "参数值必须是合法数字");
        }
    }

    public record SettingView(String key, String value, String label, String group, String type,
                              String description, Instant updatedAt) {
        public static SettingView from(SystemSetting setting) {
            return new SettingView(setting.getSettingKey(), setting.getSettingValue(), setting.getLabel(),
                    setting.getSettingGroup(), setting.getValueType(), setting.getDescription(),
                    setting.getUpdatedAt());
        }
    }
}
