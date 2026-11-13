package com.ivs.platform.organization;

import com.ivs.platform.organization.api.*;
import com.ivs.platform.security.DataScope;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    private final CompanyRepository companyRepository;
    private final StoreRepository storeRepository;
    private final TeamRepository teamRepository;

    public OrganizationService(CompanyRepository companyRepository, StoreRepository storeRepository, TeamRepository teamRepository) {
        this.companyRepository = companyRepository; this.storeRepository = storeRepository; this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true) public List<OrganizationView> companies(){return companyRepository.findAllByOrderByNameAsc().stream().map(OrganizationView::from).toList();}
    @Transactional(readOnly = true) public List<OrganizationView> stores(UUID companyId){return (companyId==null?storeRepository.findAllByOrderByNameAsc():storeRepository.findByCompanyIdOrderByNameAsc(companyId)).stream().map(OrganizationView::from).toList();}
    @Transactional(readOnly = true) public List<OrganizationView> teams(UUID storeId){return (storeId==null?teamRepository.findAllByOrderByNameAsc():teamRepository.findByStoreIdOrderByNameAsc(storeId)).stream().map(OrganizationView::from).toList();}

    @Transactional public OrganizationView createCompany(CreateCompanyCommand c){
        String code = c.code() == null ? "" : c.code().trim().toUpperCase();
        String name = c.name() == null ? "" : c.name().trim();
        if(code.isEmpty()) throw new ConflictException("COMPANY_CODE_REQUIRED","公司编码不能为空");
        if(name.isEmpty()) throw new ConflictException("COMPANY_NAME_REQUIRED","公司名称不能为空");
        if(companyRepository.existsByCodeIgnoreCase(code)) throw new ConflictException("COMPANY_CODE_DUPLICATE","公司编码已存在");
        try {
            return OrganizationView.from(companyRepository.saveAndFlush(Company.create(code,name)));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ConflictException("COMPANY_CODE_DUPLICATE","公司编码已存在");
        }
    }
    @Transactional public OrganizationView createStore(CreateStoreCommand c){
        requireCompany(c.companyId());
        if(storeRepository.existsByCompanyIdAndCode(c.companyId(),c.code().trim())) throw new ConflictException("STORE_CODE_DUPLICATE","门店编码已存在");
        return OrganizationView.from(storeRepository.save(Store.create(c.companyId(),c.code().trim(),c.name().trim())));
    }
    @Transactional public OrganizationView createTeam(CreateTeamCommand c){
        Company company=requireCompany(c.companyId()); Store store=requireStore(c.storeId());
        if(!store.getCompanyId().equals(company.getId())) throw new ConflictException("STORE_COMPANY_MISMATCH","门店不属于所选公司");
        if(teamRepository.existsByStoreIdAndCode(c.storeId(),c.code().trim())) throw new ConflictException("TEAM_CODE_DUPLICATE","团队编码已存在");
        return OrganizationView.from(teamRepository.save(Team.create(c.companyId(),c.storeId(),c.code().trim(),c.name().trim())));
    }

    @Transactional public OrganizationView disableCompany(UUID id){Company c=requireCompany(id);c.disable();companyRepository.saveAndFlush(c);return OrganizationView.from(c);}
    @Transactional public OrganizationView enableCompany(UUID id){Company c=requireCompany(id);c.enable();companyRepository.saveAndFlush(c);return OrganizationView.from(c);}
    @Transactional public OrganizationView disableStore(UUID id){Store s=requireStore(id);s.disable();storeRepository.saveAndFlush(s);return OrganizationView.from(s);}
    @Transactional public OrganizationView enableStore(UUID id){Store s=requireStore(id);s.enable();storeRepository.saveAndFlush(s);return OrganizationView.from(s);}
    @Transactional public OrganizationView disableTeam(UUID id){Team t=requireTeam(id);t.disable();teamRepository.saveAndFlush(t);return OrganizationView.from(t);}
    @Transactional public OrganizationView enableTeam(UUID id){Team t=requireTeam(id);t.enable();teamRepository.saveAndFlush(t);return OrganizationView.from(t);}

    public void validateUserOrganization(DataScope scope, UUID companyId, UUID storeId, UUID teamId) {
        if (scope == null || scope == DataScope.SELF || scope == DataScope.ALL) return;
        if (scope == DataScope.COMPANY) { requireCompany(companyId); return; }
        if (scope == DataScope.STORE) { Store store=requireStore(storeId); if(companyId!=null && !companyId.equals(store.getCompanyId())) throw new ConflictException("STORE_COMPANY_MISMATCH","门店不属于所选公司"); return; }
        if (scope == DataScope.TEAM) { Team team=requireTeam(teamId); if(!team.getCompanyId().equals(companyId) || !team.getStoreId().equals(storeId)) throw new ConflictException("TEAM_ORG_MISMATCH","团队与公司或门店不匹配"); }
    }

    private Company requireCompany(UUID id){if(id==null) throw new ConflictException("COMPANY_REQUIRED","公司不能为空");return companyRepository.findById(id).orElseThrow(()->new NotFoundException("公司",id));}
    private Store requireStore(UUID id){if(id==null) throw new ConflictException("STORE_REQUIRED","门店不能为空");return storeRepository.findById(id).orElseThrow(()->new NotFoundException("门店",id));}
    private Team requireTeam(UUID id){if(id==null) throw new ConflictException("TEAM_REQUIRED","团队不能为空");return teamRepository.findById(id).orElseThrow(()->new NotFoundException("团队",id));}
}
