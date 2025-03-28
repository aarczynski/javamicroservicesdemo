host ?= http://localhost:8081
employeesDataFile ?= ./data-generator/output/employees.sql
departmentsDataFile ?= ./data-generator/output/departments.sql

start: clean_build compose_up

clean_build:
	./gradlew clean :app-company:build :app-company-client:build

compose_up:
	docker compose up --build

employeeSimulation:
	./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.EmployeeSimulation -Dhost=$(host) -DdataFile=$(employeesDataFile)

departmentSimulation:
	./gradlew :load-test:gatlingRun --simulation pl.lunasoftware.demo.microservices.loadtest.DepartmentSimulation -Dhost=$(host) -DdataFile=$(departmentsDataFile)
