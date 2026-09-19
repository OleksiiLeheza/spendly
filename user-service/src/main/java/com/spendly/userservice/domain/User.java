package com.spendly.userservice.domain;

import com.spendly.userservice.domain.common.BaseEntityAudit;
import com.spendly.userservice.domain.enums.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User extends BaseEntityAudit {

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private Role role;
}
