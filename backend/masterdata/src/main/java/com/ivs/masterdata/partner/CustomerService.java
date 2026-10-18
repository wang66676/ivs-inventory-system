package com.ivs.masterdata.partner;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 客户档案。编码留空时自动生成 CUS-000001。 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterdataApi.CustomerView> list() {
        return customerRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(MasterdataApi.CustomerView::from).toList();
    }

    @Transactional(readOnly = true)
    public MasterdataApi.CustomerView get(UUID id) {
        return MasterdataApi.CustomerView.from(find(id));
    }

    @Transactional
    public MasterdataApi.CustomerView create(MasterdataApi.SaveCustomerCommand command) {
        String code = command.code() == null || command.code().isBlank()
                ? "CUS-%06d".formatted(customerRepository.nextCustomerSequence())
                : command.code().trim().toUpperCase();
        if (customerRepository.existsByCode(code)) {
            throw new ConflictException("CUSTOMER_CODE_DUPLICATE", "客户编码已存在：" + code);
        }
        Customer customer = Customer.create(code, command.name(), command.customerType(), command.contactName(),
                command.contactPhone(), command.taxNo(), command.creditLimit(), command.remark());
        return MasterdataApi.CustomerView.from(customerRepository.save(customer));
    }

    @Transactional
    public MasterdataApi.CustomerView update(UUID id, MasterdataApi.SaveCustomerCommand command) {
        Customer customer = find(id);
        customer.update(command.name(), command.customerType(), command.contactName(), command.contactPhone(),
                command.taxNo(), command.creditLimit(), command.remark());
        return MasterdataApi.CustomerView.from(customerRepository.save(customer));
    }

    @Transactional
    public MasterdataApi.CustomerView disable(UUID id) {
        Customer customer = find(id);
        customer.disable();
        return MasterdataApi.CustomerView.from(customerRepository.save(customer));
    }

    @Transactional
    public MasterdataApi.CustomerView enable(UUID id) {
        Customer customer = find(id);
        customer.enable();
        return MasterdataApi.CustomerView.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Customer find(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("客户", id));
    }
}