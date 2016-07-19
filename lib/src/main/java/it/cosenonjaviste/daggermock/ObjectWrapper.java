package it.cosenonjaviste.daggermock;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import javax.inject.Provider;

/**
 * Created by fabiocollini on 20/04/16.
 */
public class ObjectWrapper<T> {
    private T obj;

    public ObjectWrapper(T obj) {
        this.obj = obj;
    }

    public Method getMethodReturning(Class<?> type) {
        return ReflectUtils.getMethodReturning(obj.getClass(), type);
    }

    public <C> C invokeMethod(String methodName) {
        Method m;
        try {
            m = obj.getClass().getMethod("build");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method " + methodName + " not found in object " + obj, e);
        }
        return (C) invokeMethod(m);
    }

    public Object invokeMethod(Method m, Object... args) {
        return ReflectUtils.invokeMethod(obj, m, args);
    }

    public Method getMethodWithParameter(Class<?> parameterClass) {
        return ReflectUtils.getMethodWithParameter(obj.getClass(), parameterClass);
    }

    public void setFieldValue(Field field, Object fieldValue) {
        ReflectUtils.setFieldValue(obj, field, fieldValue);
    }

    public List<Field> extractAnnotatedFields(Class<? extends Annotation> annotationClass) {
        return ReflectUtils.extractAnnotatedFields(obj, annotationClass);
    }

    public T getValue() {
        return obj;
    }

    public Object getFieldValue(Class<?> fieldClass) {
        Field field = getField(fieldClass);
        if (field != null) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public Object getProviderFieldValue(Class<?> fieldClass) {
        Field field = getProviderField(fieldClass);
        if (field != null) {
            try {
                Provider<?> provider = (Provider<?>) field.get(obj);
                if (provider != null) {
                    return provider.get();
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public Field getProviderField(Class<?> fieldClass) {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().equals(Provider.class) && field.getGenericType() instanceof ParameterizedType) {
                Type[] actualTypeArguments = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                if (actualTypeArguments.length == 1 && actualTypeArguments[0].equals(fieldClass)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    public Object getFieldOrProviderValue(Class<?> c) {
        Object fieldValue = getFieldValue(c);
        if (fieldValue == null) {
            fieldValue = getProviderFieldValue(c);
            if (fieldValue == null) {
                throw new RuntimeException(c.getName() + " field not found in class " + getValue().getClass().getName() + ", it's defined as parameter in InjectFromComponent annotation");
            }
        }
        return fieldValue;
    }

    public Field getField(Class<?> fieldClass) {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().equals(fieldClass)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    public ObjectWrapper<T> invokeBuilderSetter(Class<?> parameterClass, Object parameter) {
        try {
            Method setMethod = getSetterMethod(obj, parameterClass);
            return new ObjectWrapper<T>((T) setMethod.invoke(obj, parameter));
        } catch (Exception e) {
            throw new RuntimeException("Error invoking setter with parameter " + parameterClass + " on object " + obj, e);
        }
    }


    public static Method getSetterMethod(Object builder, Class<?> moduleClass) throws NoSuchMethodException {
        while (true) {
            try {
                String moduleName = moduleClass.getSimpleName();
                String setterName = ReflectUtils.toCamelCase(moduleName);
                return builder.getClass().getMethod(setterName, moduleClass);
            } catch (NoSuchMethodException e) {
                moduleClass = moduleClass.getSuperclass();
                if (moduleClass.equals(Object.class)) {
                    throw e;
                }
            }
        }
    }

    public static <T> ObjectWrapper<T> newInstance(Class<T> classToInject) {
        return newInstance(classToInject, "Error instantiating class " + classToInject.getName());
    }

    public static <T> ObjectWrapper<T> newInstance(Class<T> classToInject, String message) {
        try {
            return new ObjectWrapper<>(classToInject.newInstance());
        } catch (Exception e) {
            throw new RuntimeException(message, e);
        }
    }

    public static <T> ObjectWrapper<T> invokeStaticMethod(Class<T> c, String methodName) {
        try {
            return new ObjectWrapper<T>((T) c.getMethod(methodName).invoke(null));
        } catch (Exception e) {
            throw new RuntimeException("Error while invoking static method " + methodName + " on class " + c, e);
        }
    }
}
