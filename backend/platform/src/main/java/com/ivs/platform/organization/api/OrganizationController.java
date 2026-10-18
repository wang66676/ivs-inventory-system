package com.ivs.platform.organization.api;

import com.ivs.platform.organization.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class OrganizationController {
    private final OrganizationService service;
    public OrganizationController(OrganizationService service){this.service=service;}

    @GetMapping("/companies") public List<OrganizationView> companies(){return service.companies();}
    @PostMapping("/companies") public ResponseEntity<OrganizationView> createCompany(@Valid @RequestBody CreateCompanyCommand c){OrganizationView v=service.createCompany(c);return ResponseEntity.created(URI.create("/api/platform/companies/"+v.id())).body(v);}
    @PostMapping("/companies/{id}/disable") public OrganizationView disableCompany(@PathVariable("id") UUID id){return service.disableCompany(id);}
    @PostMapping("/companies/{id}/enable") public OrganizationView enableCompany(@PathVariable("id") UUID id){return service.enableCompany(id);}

    @GetMapping("/stores") public List<OrganizationView> stores(@RequestParam(required=false) UUID companyId){return service.stores(companyId);}
    @PostMapping("/stores") public ResponseEntity<OrganizationView> createStore(@Valid @RequestBody CreateStoreCommand c){OrganizationView v=service.createStore(c);return ResponseEntity.created(URI.create("/api/platform/stores/"+v.id())).body(v);}
    @PostMapping("/stores/{id}/disable") public OrganizationView disableStore(@PathVariable("id") UUID id){return service.disableStore(id);}
    @PostMapping("/stores/{id}/enable") public OrganizationView enableStore(@PathVariable("id") UUID id){return service.enableStore(id);}

    @GetMapping("/teams") public List<OrganizationView> teams(@RequestParam(required=false) UUID storeId){return service.teams(storeId);}
    @PostMapping("/teams") public ResponseEntity<OrganizationView> createTeam(@Valid @RequestBody CreateTeamCommand c){OrganizationView v=service.createTeam(c);return ResponseEntity.created(URI.create("/api/platform/teams/"+v.id())).body(v);}
    @PostMapping("/teams/{id}/disable") public OrganizationView disableTeam(@PathVariable("id") UUID id){return service.disableTeam(id);}
    @PostMapping("/teams/{id}/enable") public OrganizationView enableTeam(@PathVariable("id") UUID id){return service.enableTeam(id);}
}
