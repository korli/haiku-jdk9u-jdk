/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.invoke;

import java.util.Map;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Helper class to assist the GenerateJLIClassesPlugin to get access to
 * generate classes ahead of time.
 */
class GenerateJLIClassesHelper {

    static byte[] generateDirectMethodHandleHolderClassBytes(String className,
            MethodType[] methodTypes, int[] types) {
        LambdaForm[] forms = new LambdaForm[methodTypes.length];
        String[] names = new String[methodTypes.length];
        for (int i = 0; i < forms.length; i++) {
            forms[i] = DirectMethodHandle.makePreparedLambdaForm(methodTypes[i],
                                                                 types[i]);
            names[i] = forms[i].kind.defaultLambdaName;
        }
        return generateCodeBytesForLFs(className, names, forms);
    }

    static byte[] generateDelegatingMethodHandleHolderClassBytes(String className,
            MethodType[] methodTypes) {

        HashSet<MethodType> dedupSet = new HashSet<>();
        ArrayList<LambdaForm> forms = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < methodTypes.length; i++) {
            // generate methods representing the DelegatingMethodHandle
            if (dedupSet.add(methodTypes[i])) {
                // reinvokers are variant with the associated SpeciesData
                // and shape of the target LF, but we can easily pregenerate
                // the basic reinvokers associated with Species_L. Ultimately we
                // may want to consider pregenerating more of these, which will
                // require an even more complex naming scheme
                LambdaForm reinvoker = makeReinvokerFor(methodTypes[i]);
                forms.add(reinvoker);
                String speciesSig = BoundMethodHandle
                        .speciesData(reinvoker).fieldSignature();
                assert(speciesSig.equals("L"));
                names.add(reinvoker.kind.defaultLambdaName + "_" + speciesSig);

                LambdaForm delegate = makeDelegateFor(methodTypes[i]);
                forms.add(delegate);
                names.add(delegate.kind.defaultLambdaName);
            }
        }
        return generateCodeBytesForLFs(className,
                names.toArray(new String[0]),
                forms.toArray(new LambdaForm[0]));
    }

    /*
     * Generate customized code for a set of LambdaForms of specified types into
     * a class with a specified name.
     */
    private static byte[] generateCodeBytesForLFs(String className,
            String[] names, LambdaForm[] forms) {

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER,
                className, null, InvokerBytecodeGenerator.INVOKER_SUPER_NAME, null);
        cw.visitSource(className.substring(className.lastIndexOf('/') + 1), null);

        for (int i = 0; i < forms.length; i++) {
            addMethod(className, names[i], forms[i],
                    forms[i].methodType(), cw);
        }
        return cw.toByteArray();
    }

    private static void addMethod(String className, String methodName, LambdaForm form,
            MethodType type, ClassWriter cw) {
        InvokerBytecodeGenerator g
                = new InvokerBytecodeGenerator(className, methodName, form, type);
        g.setClassWriter(cw);
        g.addMethod();
    }

    private static LambdaForm makeReinvokerFor(MethodType type) {
        MethodHandle emptyHandle = MethodHandles.empty(type);
        return DelegatingMethodHandle.makeReinvokerForm(emptyHandle,
                MethodTypeForm.LF_REBIND,
                BoundMethodHandle.speciesData_L(),
                BoundMethodHandle.speciesData_L().getterFunction(0));
    }

    private static LambdaForm makeDelegateFor(MethodType type) {
        MethodHandle handle = MethodHandles.empty(type);
        return DelegatingMethodHandle.makeReinvokerForm(
                handle,
                MethodTypeForm.LF_DELEGATE,
                DelegatingMethodHandle.class,
                DelegatingMethodHandle.NF_getTarget);
    }

    static Map.Entry<String, byte[]> generateConcreteBMHClassBytes(
            final String types) {
        for (char c : types.toCharArray()) {
            if ("LIJFD".indexOf(c) < 0) {
                throw new IllegalArgumentException("All characters must "
                        + "correspond to a basic field type: LIJFD");
            }
        }
        String shortTypes = LambdaForm.shortenSignature(types);
        final String className =
                BoundMethodHandle.Factory.speciesInternalClassName(shortTypes);
        return Map.entry(className,
                         BoundMethodHandle.Factory.generateConcreteBMHClassBytes(
                                 shortTypes, types, className));
    }
}
