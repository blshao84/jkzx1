package tech.tongyu.core.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import java.util.HashSet;
import java.util.Set;

public class ImportScanner extends ElementScanner8<Void, Void> {
    private Set<String> types = new HashSet<>();

    public Set<String> getTypes() {
        return types;
    }

    @Override
    public Void visitType(TypeElement e, Void p) {
        for (TypeMirror interfaceType : e.getInterfaces()) {
            types.add(interfaceType.toString());
        }
        types.add(e.getSuperclass().toString());
        return super.visitType(e, p);
    }

    @Override
    public Void visitExecutable(ExecutableElement e, Void p) {
        if (e.getReturnType().getKind() == TypeKind.DECLARED) {
            types.add(e.getReturnType().toString());
        }
        return super.visitExecutable(e, p);
    }

    @Override
    public Void visitTypeParameter(TypeParameterElement e, Void p) {
        if (e.asType().getKind() == TypeKind.DECLARED) {
            types.add(e.asType().toString());
        }
        return super.visitTypeParameter(e, p);
    }

    @Override
    public Void visitVariable(VariableElement e, Void p) {
        if (e.asType().getKind() == TypeKind.DECLARED) {
            types.add(e.asType().toString());
        }
        return super.visitVariable(e, p);
    }
}
