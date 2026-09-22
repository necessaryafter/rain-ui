package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record ComponentNode(String type, Map<String, JsonNode> props, List<ComponentNode> children) {}
