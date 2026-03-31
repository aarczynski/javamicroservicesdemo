package pl.lunasoftware.demo.microservices.datagenerator.generator.skill;

import java.util.UUID;

public class SkillGenerator {

    public static final String[] SKILL_NAMES = {
            "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++", "C#", "Kotlin", "Swift",
            "React", "Angular", "Vue.js", "Node.js", "Spring Boot", "Django", "FastAPI", ".NET",
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Cassandra", "Oracle DB",
            "Kubernetes", "Docker", "Terraform", "AWS", "Azure", "GCP",
            "Kafka", "RabbitMQ", "gRPC", "REST API", "GraphQL",
            "Git", "CI/CD", "Jenkins", "GitHub Actions", "GitLab CI",
            "Linux", "Bash",
            "Machine Learning", "Deep Learning", "Data Science", "TensorFlow", "PyTorch",
            "Microservices", "System Design", "Clean Architecture", "TDD", "DDD"
    };

    public Skill[] skills() {
        Skill[] result = new Skill[SKILL_NAMES.length];
        for (int i = 0; i < SKILL_NAMES.length; i++) {
            result[i] = new Skill(UUID.randomUUID(), SKILL_NAMES[i]);
        }
        return result;
    }
}
