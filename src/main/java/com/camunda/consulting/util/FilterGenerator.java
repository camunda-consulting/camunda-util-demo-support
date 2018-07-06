package com.camunda.consulting.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.task.TaskQuery;

public class FilterGenerator {

  public static String FILTER_myTasks = "FILTER_myTasks";
  public static String FILTER_groupTasksFilter = "FILTER_groupTasksFilter";
  public static String FILTER_followUp = "FILTER_followUp";
  public static String FILTER_overdue = "FILTER_overdue";
  public static String FILTER_management = "FILTER_management";
  public static String FILTER_allTasksFilter = "FILTER_allTasksFilter";
  public static String FILTER_MeineAufgaben = "FILTER_MeineAufgaben";
  public static String FILTER_GruppenAufgaben = "FILTER_GruppenAufgaben";
  public static String FILTER_Wiedervorlage = "FILTER_Wiedervorlage";
  public static String FILTER_Ueberfaellig = "FILTER_Ueberfaellig";
  public static String FILTER_PostkorbManagement = "FILTER_PostkorbManagement";
  public static String FILTER_alleAufgaben = "FILTER_alleAufgaben";

  public static Map<String, String> filterIds = new HashMap<String, String>();

  public static String useFilter(ProcessEngine engine, String filterName) {
    if (filterIds.containsKey(filterName)) {
      return filterIds.get(filterName);
    } else {
      String filterId = createFilter(engine, filterName);
      filterIds.put(filterName, filterId);
      return filterId;
    }
  }

  private static String createFilter(ProcessEngine engine, String filterName) {
    if (FILTER_groupTasksFilter.equals(filterName)) {
      return createFilter(engine, "My Group Tasks", -20, "Tasks assigned to my Groups", //
          engine.getTaskService().createTaskQuery().taskCandidateGroupInExpression("${currentUserGroups()}").taskUnassigned());
    }
    if (FILTER_myTasks.equals(filterName)) {
      return createFilter(engine, "My Tasks", -10, "Tasks assigned to me", // +
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}"));
    }
    if (FILTER_followUp.equals(filterName)) {
      return createFilter(engine, "Follow-Up", 5, "Task with follow-up date", //
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}").followUpAfterExpression("${now()}"));
    }
    if (FILTER_overdue.equals(filterName)) {
      return createFilter(engine, "Overdue", 10, "Overdue Tasks", //
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}").dueBeforeExpression("${now()}"), //
          "color", "#b5152b");
    }
    if (FILTER_management.equals(filterName)) {
      return createFilter(engine, "Management", 0, "Tasks for 'Management'", //
          engine.getTaskService().createTaskQuery().taskCandidateGroupIn(Arrays.asList("management")).taskUnassigned());
    }
    if (FILTER_allTasksFilter.equals(filterName)) {
      return createFilter(engine, "All Tasks", 20, "All Tasks (not recommended to be used in production)", //
          engine.getTaskService().createTaskQuery());
    }

    if (FILTER_GruppenAufgaben.equals(filterName)) {
      return createFilter(engine, "Meine Gruppen", -20, "Aufgaben in allen meinen Gruppen", //
          engine.getTaskService().createTaskQuery().taskCandidateGroupInExpression("${currentUserGroups()}").taskUnassigned());
    }
    if (FILTER_MeineAufgaben.equals(filterName)) {
      return createFilter(engine, "Persönlich", -10, "Meine persönlichen Aufgaben", //
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}").followUpBeforeOrNotExistentExpression("${now()}"));
    }
    if (FILTER_Wiedervorlage.equals(filterName)) {
      return createFilter(engine, "Wiedervorlage", 5, "Auf Wiedervorlage gelegte Aufgaben", //
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}").followUpAfterExpression("${now()}"));
    }
    if (FILTER_Ueberfaellig.equals(filterName)) {
      return createFilter(engine, "Überfällig", 10, "Überfällige Aufgaben", //
          engine.getTaskService().createTaskQuery().taskAssigneeExpression("${currentUser()}").dueBeforeExpression("${now()}"), //
          "color", "#b5152b");
    }
    if (FILTER_PostkorbManagement.equals(filterName)) {
      return createFilter(engine, "Management", 0, "Aufgaben für 'Management'", //
          engine.getTaskService().createTaskQuery().taskCandidateGroupIn(Arrays.asList("management")).taskUnassigned());
    }
    if (FILTER_alleAufgaben.equals(filterName)) {
      return createFilter(engine, "Alle Aufgaben", 20, "Alle Aufgaben (z.B. für Team-Leiter)", //
          engine.getTaskService().createTaskQuery());
    }
    throw new RuntimeException("Filter with name '" + filterName + "' not created or foreseen (use FilterGenerator.createFilter to create filter before usage)");
  }

  public static String createFilter(ProcessEngine engine, String name, int priority, String description, TaskQuery query, Object... additionalProperties) {
    return createFilter(engine, name, priority, description, query, new ArrayList<Map<String, String>>(), additionalProperties);
  }

  public static String createFilter(ProcessEngine engine, String name, int priority, String description, TaskQuery query, List<Map<String, String>> variables, Object... additionalProperties) {
    Filter existingFilter = engine.getFilterService().createFilterQuery().filterName(name).singleResult();
    if (existingFilter!=null) {
      return existingFilter.getId();
    }
     
    Map<String, Object> filterProperties = new HashMap<String, Object>();

    filterProperties.put("description", description);
    filterProperties.put("priority", priority);

    String key = null;
    for (Object additionalProperty : additionalProperties) {
      if (key == null) {
        key = (String) additionalProperty;
      } else {
        filterProperties.put(key, additionalProperty);
        key = null;
      }
    }

    filterProperties.put("variables", variables);

    Filter myTasksFilter = engine.getFilterService().newTaskFilter() //
        .setName(name) //
        .setProperties(filterProperties)//
        .setOwner("admin")//
        .setQuery(query);
    engine.getFilterService().saveFilter(myTasksFilter);
    return myTasksFilter.getId();
  }

  public static List<Map<String, String>> createFilterVariables(String... variableNamesOrLabels) {
    List<Map<String, String>> variables = new ArrayList<Map<String, String>>();
    for (int i = 0; i < variableNamesOrLabels.length; i += 2) {
      String name = variableNamesOrLabels[i];
      String label = name;
      if (variableNamesOrLabels.length > i + 1) {
        label = variableNamesOrLabels[i + 1];
      }
      variables.add(createFilterVariable(name, label));
    }
    return variables;
  }
  
  public static Map<String, String> createFilterVariable(String name, String label) {
    Map<String, String> variablePrimaryContract = new HashMap<String, String>();
    variablePrimaryContract.put("name", name);
    variablePrimaryContract.put("label", label);
    return variablePrimaryContract;
  }

}
