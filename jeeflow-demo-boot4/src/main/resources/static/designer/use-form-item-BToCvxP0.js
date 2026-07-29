import { getCurrentInstance, computed, inject, unref, ref, onMounted, watch, toRef, onUnmounted } from "vue";
import { b as useGlobalSize } from "./style-_T5b_00u.js";
import { f as formContextKey, a as formItemContextKey } from "./constants-BXBCHRH4.js";
import { u as useId } from "./index-DuyY_KOz.js";
const useProp = (name) => {
  const vm = getCurrentInstance();
  return computed(() => {
    var _a, _b;
    return (_b = (_a = vm == null ? void 0 : vm.proxy) == null ? void 0 : _a.$props) == null ? void 0 : _b[name];
  });
};
const useFormSize = (fallback, ignore = {}) => {
  const emptyRef = ref(void 0);
  const size = ignore.prop ? emptyRef : useProp("size");
  const globalConfig = ignore.global ? emptyRef : useGlobalSize();
  const form = ignore.form ? { size: void 0 } : inject(formContextKey, void 0);
  const formItem = ignore.formItem ? { size: void 0 } : inject(formItemContextKey, void 0);
  return computed(() => size.value || unref(fallback) || (formItem == null ? void 0 : formItem.size) || (form == null ? void 0 : form.size) || globalConfig.value || "");
};
const useFormDisabled = (fallback) => {
  const disabled = useProp("disabled");
  const form = inject(formContextKey, void 0);
  return computed(() => {
    return disabled.value ?? unref(fallback) ?? (form == null ? void 0 : form.disabled) ?? false;
  });
};
const useFormItem = () => {
  return {
    form: inject(formContextKey, void 0),
    formItem: inject(formItemContextKey, void 0)
  };
};
const useFormItemInputId = (props, { formItemContext, disableIdGeneration, disableIdManagement }) => {
  if (!disableIdGeneration) disableIdGeneration = ref(false);
  if (!disableIdManagement) disableIdManagement = ref(false);
  const instance = getCurrentInstance();
  const inLabel = () => {
    let parent = instance == null ? void 0 : instance.parent;
    while (parent) {
      if (parent.type.name === "ElFormItem") return false;
      if (parent.type.name === "ElLabelWrap") return true;
      parent = parent.parent;
    }
    return false;
  };
  const inputId = ref();
  let idUnwatch = void 0;
  const isLabeledByFormItem = computed(() => {
    var _a;
    return !!(!(props.label || props.ariaLabel) && formItemContext && formItemContext.inputIds && ((_a = formItemContext.inputIds) == null ? void 0 : _a.length) <= 1);
  });
  onMounted(() => {
    idUnwatch = watch([toRef(props, "id"), disableIdGeneration], ([id, disableIdGeneration2]) => {
      const newId = id ?? (!disableIdGeneration2 ? useId().value : void 0);
      if (newId !== inputId.value) {
        if ((formItemContext == null ? void 0 : formItemContext.removeInputId) && !inLabel()) {
          inputId.value && formItemContext.removeInputId(inputId.value);
          if (!(disableIdManagement == null ? void 0 : disableIdManagement.value) && !disableIdGeneration2 && newId) formItemContext.addInputId(newId);
        }
        inputId.value = newId;
      }
    }, { immediate: true });
  });
  onUnmounted(() => {
    idUnwatch && idUnwatch();
    if (formItemContext == null ? void 0 : formItemContext.removeInputId) inputId.value && formItemContext.removeInputId(inputId.value);
  });
  return {
    isLabeledByFormItem,
    inputId
  };
};
export {
  useFormSize as a,
  useFormDisabled as b,
  useFormItemInputId as c,
  useFormItem as u
};
//# sourceMappingURL=use-form-item-BToCvxP0.js.map
