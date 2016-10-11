package com.camunda.demo.environment.simulation;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.logging.Logger;

import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.cmmn.Cmmn;
import org.camunda.bpm.model.cmmn.CmmnModelInstance;
import org.camunda.bpm.model.cmmn.instance.CasePlanModel;
import org.camunda.bpm.model.cmmn.instance.ConditionExpression;
import org.camunda.bpm.model.cmmn.instance.DecisionTask;
import org.camunda.bpm.model.cmmn.instance.ExtensionElements;
import org.camunda.bpm.model.cmmn.instance.HumanTask;
import org.camunda.bpm.model.cmmn.instance.Sentry;
import org.camunda.bpm.model.cmmn.instance.camunda.CamundaCaseExecutionListener;
import org.camunda.bpm.model.cmmn.instance.camunda.CamundaScript;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class InstrumentCmmnHelper {

  private static final Logger log = Logger.getLogger(InstrumentCmmnHelper.class.getName());

  private ProcessEngineImpl engine;
  private String caseDefinitionKey;

  private ProcessApplicationReference processApplicationReference;
  private CaseDefinition caseDefinition;
  private String originalCmmn;

  public InstrumentCmmnHelper(ProcessEngine engine, String caseDefinitionKey, ProcessApplicationReference processApplicationReference) {
    this.engine = (ProcessEngineImpl) engine;
    this.caseDefinitionKey = caseDefinitionKey;
    this.processApplicationReference = processApplicationReference;
  }

  protected void tweakCaseDefinition() {
    log.info("tweak case definition " + caseDefinitionKey);

    caseDefinition = engine.getRepositoryService().createCaseDefinitionQuery() //
        .caseDefinitionKey(caseDefinitionKey) //
        .latestVersion() //
        .singleResult();
    if (caseDefinition == null) {
      throw new RuntimeException("Case with key '" + caseDefinitionKey + "' not found.");
    }
    // store original process application reference
    if (processApplicationReference == null) {
      processApplicationReference = engine.getProcessEngineConfiguration().getProcessApplicationManager()
          .getProcessApplicationForDeployment(caseDefinition.getDeploymentId());
    }

    CmmnModelInstance cmmn = engine.getRepositoryService().getCmmnModelInstance(caseDefinition.getId());

    originalCmmn = IoUtil.convertXmlDocumentToString(cmmn.getDocument());
    // do not do a validation here as it caused quite strange trouble
    log.finer("-----\n" + originalCmmn + "\n------");

    CasePlanModel casePlanModel = (CasePlanModel) cmmn.getModelElementsByType(cmmn.getModel().getType(CasePlanModel.class)).iterator().next();

    Collection<ModelElementInstance> sentries = cmmn.getModelElementsByType(cmmn.getModel().getType(Sentry.class));
    Collection<ModelElementInstance> businessRuleTasks = cmmn.getModelElementsByType(cmmn.getModel().getType(DecisionTask.class));
    Collection<ModelElementInstance> userTasks = cmmn.getModelElementsByType(cmmn.getModel().getType(HumanTask.class));

    for (ModelElementInstance modelElementInstance : sentries) {
      Sentry sentry = ((Sentry) modelElementInstance);
      if (sentry.getIfPart() != null && sentry.getIfPart().getCondition() != null) {
        tweakSentry(casePlanModel, sentry);
      }
    }
    // for (ModelElementInstance modelElementInstance : businessRuleTasks) {
    // DecisionTask businessRuleTask = (DecisionTask) modelElementInstance;
    // businessRuleTask.removeAttributeNs("http://activiti.org/bpmn",
    // "decisionRef");
    // businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn",
    // "decisionRef");
    // businessRuleTask.removeAttributeNs("http://activiti.org/bpmn",
    // "delegateExpression");
    // businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn",
    // "delegateExpression");
    // businessRuleTask.setDecisionExpression("#{true}"); // Noop
    // }
    // for (ModelElementInstance modelElementInstance : executionListeners) {
    // CamundaExecutionListener executionListener = (CamundaExecutionListener)
    // modelElementInstance;
    // // executionListener.setCamundaClass(null);
    // executionListener.removeAttributeNs("http://activiti.org/bpmn", "class");
    // executionListener.removeAttributeNs("http://camunda.org/schema/1.0/bpmn",
    // "class");
    //
    // executionListener.removeAttributeNs("http://activiti.org/bpmn",
    // "delegateExpression");
    // executionListener.removeAttributeNs("http://camunda.org/schema/1.0/bpmn",
    // "delegateExpression");
    // executionListener.setCamundaExpression("#{true}"); // Noop
    // }

    for (ModelElementInstance modelElementInstance : userTasks) {
      HumanTask userTask = ((HumanTask) modelElementInstance);
      userTask.setCamundaAssignee(null);
      userTask.setCamundaCandidateGroups(null);
    }

    // Bpmn.validateModel(bpmn);
    String xmlString = Cmmn.convertToString(cmmn);
    try {
      Deployment deployment = engine.getRepositoryService().createDeployment() //
          // .addString(processDefinitionKey + ".bpmn", xmlString) //
          // .addModelInstance(processDefinitionKey + ".bpmn", bpmn) //
          .addInputStream(caseDefinitionKey + ".cmmn", new ByteArrayInputStream(xmlString.getBytes("UTF-8"))).deploy();
      if (processApplicationReference!=null) {
        engine.getManagementService().registerProcessApplication(deployment.getId(), processApplicationReference);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Could not deploy tweaked case definition", ex);
    }
  }

  protected void tweakSentry(CasePlanModel casePlanModel, Sentry sentry) {
    CmmnModelInstance cmmn = (CmmnModelInstance) sentry.getModelInstance();

    String var = "SIM_SAMPLE_VALUE_" + sentry.getId().replaceAll("-", "_");

    // let's toggle with a 50/50 for the beginning

    ConditionExpression conditionExpression = cmmn.newInstance(ConditionExpression.class);
    conditionExpression.setTextContent("#{" + var + " >= 1 }");
    sentry.getIfPart().setCondition(conditionExpression);

    // add execution listener to set variable based on random
    CamundaCaseExecutionListener executionListener = cmmn.newInstance(CamundaCaseExecutionListener.class);
    executionListener.setCamundaEvent("create");
    CamundaScript script = cmmn.newInstance(CamundaScript.class);
    script.setTextContent(//
        "sample = com.camunda.demo.environment.simulation.StatisticsHelper.nextSample(" + 2 + ");\n" + "caseExecution.setVariable('" + var + "', sample);");
    script.setCamundaScriptFormat("Javascript");
    executionListener.setCamundaScript(script);

//    CmmnElementImpl parentElement = (CmmnElementImpl) sentry.getParentElement();

    if (casePlanModel.getExtensionElements() == null) {
      ExtensionElements extensionElements = cmmn.newInstance(ExtensionElements.class);
      casePlanModel.addChildElement(extensionElements);
    }
    casePlanModel.getExtensionElements().addChildElement(executionListener);

  }

  public void restoreOriginalCaseDefinition() {
    log.info("restore original case definition");

    try {
      Deployment deployment = engine.getRepositoryService().createDeployment() //
          .addInputStream(caseDefinitionKey + ".cmmn", new ByteArrayInputStream(originalCmmn.getBytes("UTF-8"))) //
          .deploy();
      if (processApplicationReference != null) {
        engine.getManagementService().registerProcessApplication(deployment.getId(), processApplicationReference);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Could not deploy tweaked process definition", ex);
    }
  }
}
