package com.camunda.demo.environment.simulation;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.logging.Logger;

import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.BusinessRuleTask;
import org.camunda.bpm.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaScript;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class InstrumentBpmnHelper {

  private static final Logger log = Logger.getLogger(TimeAwareDemoGenerator.class.getName());
  private ProcessEngineImpl engine;
  private String processDefinitionKey;

  private ProcessApplicationReference processApplicationReference;
  private ProcessDefinition processDefinition;
  private String originalBpmn;

  public InstrumentBpmnHelper(ProcessEngine engine, String processDefinitionKey) {
    this.engine = (ProcessEngineImpl)engine;
    this.processDefinitionKey = processDefinitionKey;
  }

  protected void tweakProcessDefinition() {
    log.info("tweak process definition " + processDefinitionKey);

    processDefinition = engine.getRepositoryService().createProcessDefinitionQuery() //
        .processDefinitionKey(processDefinitionKey) //
        .latestVersion() //
        .singleResult();
    if (processDefinition == null) {
      throw new RuntimeException("Process with key '" + processDefinitionKey + "' not found.");
    }
    // store original process application reference
    if (processApplicationReference == null) {
      processApplicationReference = engine.getProcessEngineConfiguration().getProcessApplicationManager()
          .getProcessApplicationForDeployment(processDefinition.getDeploymentId());
    }

    BpmnModelInstance bpmn = engine.getRepositoryService().getBpmnModelInstance(processDefinition.getId());

    originalBpmn = IoUtil.convertXmlDocumentToString(bpmn.getDocument());
    // do not do a validation here as it caused quite strange trouble
    log.finer("-----\n" + originalBpmn + "\n------");

    Collection<ModelElementInstance> serviceTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(ServiceTask.class));
    Collection<ModelElementInstance> sendTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(SendTask.class));
    Collection<ModelElementInstance> receiveTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(ReceiveTask.class));
    Collection<ModelElementInstance> businessRuleTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(BusinessRuleTask.class));
    Collection<ModelElementInstance> scriptTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(ScriptTask.class));
    Collection<ModelElementInstance> userTasks = bpmn.getModelElementsByType(bpmn.getModel().getType(UserTask.class));
    Collection<ModelElementInstance> executionListeners = bpmn.getModelElementsByType(bpmn.getModel().getType(CamundaExecutionListener.class));
    Collection<ModelElementInstance> taskListeners = bpmn.getModelElementsByType(bpmn.getModel().getType(CamundaTaskListener.class));
    Collection<ModelElementInstance> xorGateways = bpmn.getModelElementsByType(bpmn.getModel().getType(ExclusiveGateway.class));
    Collection<ModelElementInstance> orGateways = bpmn.getModelElementsByType(bpmn.getModel().getType(InclusiveGateway.class));

    Collection<ModelElementInstance> scripts = bpmn.getModelElementsByType(bpmn.getModel().getType(CamundaScript.class));

    for (ModelElementInstance modelElementInstance : serviceTasks) {
      ServiceTask serviceTask = ((ServiceTask) modelElementInstance);
      serviceTask.setCamundaClass(null);
      // TODO: Wait for https://app.camunda.com/jira/browse/CAM-4178 and set
      // to null!
      // serviceTask.setCamundaDelegateExpression(null);
      // Workaround:
      serviceTask.removeAttributeNs("http://activiti.org/bpmn", "delegateExpression");
      serviceTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression");

      serviceTask.setCamundaExpression("#{true}"); // Noop
    }
    for (ModelElementInstance modelElementInstance : sendTasks) {
      SendTask serviceTask = ((SendTask) modelElementInstance);
      serviceTask.setCamundaClass(null);
      serviceTask.removeAttributeNs("http://activiti.org/bpmn", "delegateExpression");
      serviceTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression");
      serviceTask.setCamundaExpression("#{true}"); // Noop
    }
    for (ModelElementInstance modelElementInstance : businessRuleTasks) {
      BusinessRuleTask businessRuleTask = (BusinessRuleTask) modelElementInstance;
      businessRuleTask.removeAttributeNs("http://activiti.org/bpmn", "decisionRef"); // DMN
                                                                                     // ref
                                                                                     // from
                                                                                     // 7.4
                                                                                     // on
      businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "decisionRef"); // DMN
                                                                                               // ref
                                                                                               // from
                                                                                               // 7.4
                                                                                               // on
      businessRuleTask.setCamundaClass(null);
      businessRuleTask.removeAttributeNs("http://activiti.org/bpmn", "delegateExpression");
      businessRuleTask.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression");

      businessRuleTask.setCamundaExpression("#{true}"); // Noop
    }
    for (ModelElementInstance modelElementInstance : executionListeners) {
      CamundaExecutionListener executionListener = (CamundaExecutionListener) modelElementInstance;
      // executionListener.setCamundaClass(null);
      executionListener.removeAttributeNs("http://activiti.org/bpmn", "class");
      executionListener.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "class");

      executionListener.removeAttributeNs("http://activiti.org/bpmn", "delegateExpression");
      executionListener.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "delegateExpression");
      executionListener.setCamundaExpression("#{true}"); // Noop
    }
    for (ModelElementInstance modelElementInstance : scripts) {
      CamundaScript script = (CamundaScript) modelElementInstance;
      // executionListener.setCamundaClass(null);
      script.setTextContent(""); // java.lang.System.out.println('x');
      script.setCamundaScriptFormat("javascript");
      script.removeAttributeNs("http://activiti.org/bpmn", "resource");
      script.removeAttributeNs("http://camunda.org/schema/1.0/bpmn", "resource");
    }

    for (ModelElementInstance modelElementInstance : userTasks) {
      UserTask userTask = ((UserTask) modelElementInstance);
      userTask.setCamundaAssignee(null);
      userTask.setCamundaCandidateGroups(null);
    }

    for (ModelElementInstance modelElementInstance : xorGateways) {
      ExclusiveGateway xorGateway = ((ExclusiveGateway) modelElementInstance);
      tweakGateway(xorGateway);
    }

    // Bpmn.validateModel(bpmn);
    String xmlString = Bpmn.convertToString(bpmn);
    try {
      engine.getRepositoryService().createDeployment() //
          // .addString(processDefinitionKey + ".bpmn", xmlString) //
          // .addModelInstance(processDefinitionKey + ".bpmn", bpmn) //
          .addInputStream(processDefinitionKey + ".bpmn", new ByteArrayInputStream(xmlString.getBytes("UTF-8"))).deploy();
    } catch (Exception ex) {
      throw new RuntimeException("Could not deploy tweaked process definition", ex);
    }
  }

  protected void tweakGateway(ExclusiveGateway xorGateway) {
    ModelInstance bpmn = xorGateway.getModelInstance();

    double probabilitySum = 0;
    // Process Variable used to store sample from distribution to decide for
    // outgoing transition
    String var = "SIM_SAMPLE_VALUE_" + xorGateway.getId().replaceAll("-", "_");

    Collection<SequenceFlow> flows = xorGateway.getOutgoing();
    if (flows.size() > 1) { // if outgoing flows = 1 it is a joining gateway
      for (SequenceFlow sequenceFlow : flows) {
        String camundaProperty = readCamundaProperty(sequenceFlow, "probability");
        double probability = 1; // default
        if (camundaProperty != null) {
          probability = Double.valueOf(camundaProperty);
        }

        ConditionExpression conditionExpression = bpmn.newInstance(ConditionExpression.class);
        conditionExpression.setTextContent("#{" + var + " >= " + probabilitySum + " && " + var + " < " + (probabilitySum + probability) + "}");
        sequenceFlow.setConditionExpression(conditionExpression);

        probabilitySum += probability;
      }

      // add execution listener to do decision based on random which corresponds
      // to configured probabilities
      // (because of expressions on outgoing sequence flows)
      CamundaExecutionListener executionListener = bpmn.newInstance(CamundaExecutionListener.class);
      executionListener.setCamundaEvent("start");
      CamundaScript script = bpmn.newInstance(CamundaScript.class);
      script.setTextContent(//
          "sample = com.camunda.demo.environment.simulation.StatisticsHelper.nextSample(" + probabilitySum + ");\n" + "execution.setVariable('" + var
              + "', sample);");
      script.setCamundaScriptFormat("Javascript");
      executionListener.setCamundaScript(script);

      if (xorGateway.getExtensionElements() == null) {
        ExtensionElements extensionElements = bpmn.newInstance(ExtensionElements.class);
        xorGateway.addChildElement(extensionElements);
      }
      xorGateway.getExtensionElements().addChildElement(executionListener);
    }
  }

  public static String readCamundaProperty(BaseElement modelElementInstance, String propertyName) {
    if (modelElementInstance.getExtensionElements() == null) {
      return null;
    }
    Collection<CamundaProperty> properties = modelElementInstance.getExtensionElements().getElementsQuery() //
        .filterByType(CamundaProperties.class) //
        .singleResult() //
        .getCamundaProperties();
    for (CamundaProperty property : properties) {
      // in 7.1 one has to use: property.getAttributeValue("name")
      if (propertyName.equals(property.getCamundaName())) {
        return property.getCamundaValue();
      }
    }
    return null;
  }

  public void restoreOriginalProcessDefinition() {
    log.info("restore original process definition");
    
    try {
      Deployment deployment = engine.getRepositoryService().createDeployment() //
          .addInputStream(processDefinitionKey + ".bpmn", new ByteArrayInputStream(originalBpmn.getBytes("UTF-8"))) //
          .deploy();
      if (processApplicationReference != null) {
        engine.getManagementService().registerProcessApplication(deployment.getId(), processApplicationReference);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Could not deploy tweaked process definition",  ex);
    }
  }
}
