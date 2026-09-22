package com.rainframework.ui.protocol;

import java.util.Map;

public sealed interface TypeSchema
    permits TypeSchema.StringType, TypeSchema.IntType, TypeSchema.LongType,
            TypeSchema.BoolType, TypeSchema.ItemType, TypeSchema.ListType, TypeSchema.ObjectType {

  record StringType() implements TypeSchema {}

  record IntType() implements TypeSchema {}

  record LongType() implements TypeSchema {}

  record BoolType() implements TypeSchema {}

  record ItemType() implements TypeSchema {}

  record ListType(TypeSchema of) implements TypeSchema {}

  record ObjectType(Map<String, TypeSchema> fields) implements TypeSchema {}
}
