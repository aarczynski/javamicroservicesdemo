package pl.lunasoftware.demo.microservices.datagenerator;

import pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.writer.DataFileWriter;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        int departments = DepartmentGenerator.MAX_DEPARTMENTS;
        int employees = 100_000;

        if (args.length == 1) {
            try {
                employees = Integer.parseInt(args[0]);
            } catch (Exception e) {
                System.out.printf("Unable to parse argument. Using default (%d employees).%n", employees);
            }
        } else {
            System.out.printf("No arguments provided. Using default (%d employees).%n", employees);
        }

        new DataFileWriter().writeRandomData(departments, employees);

    }
}
