package pl.lunasoftware.demo.microservices.datagenerator.generator.offer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel;

import java.math.BigDecimal;
import java.util.UUID;

public record JobOfferSkillAssignment(
        UUID id,
        UUID jobOfferId,
        UUID skillId,
        SeniorityLevel requiredSeniorityLevel,
        boolean mandatory,
        BigDecimal weight
) {}
