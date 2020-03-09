/*
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* eslint-disable import/no-unresolved, import/default */

import tenantFieldsetTemplate from './tenant-fieldset.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function TenantDirective($compile, $templateCache, $translate, toast, $mdExpansionPanel, tenantService, types) {

    var linker = function (scope, element) {
        scope.ruleEngineSettingsId = (Math.random()*1000).toFixed(0);
        scope.$mdExpansionPanel = $mdExpansionPanel;
        scope.$mdExpansionPanel().waitFor(scope.ruleEngineSettingsId).then((instance) => {
            instance.expand();
        });

        scope.types = types;

        let tenantPartitionsNumber = 10;
        scope.changeExecutionType = function() {
            if (scope.tenant.ruleEngineSettings.executionType == types.ruleEngine.executionTypes.shared.value) {
                scope.tenant.ruleEngineSettings.partitionsNumber = tenantPartitionsNumber;
            } else {
                scope.tenant.ruleEngineSettings.partitionsNumber = undefined;
            }
        };

        tenantService.getTenantPartitionsNumber().then(
            function (response) {
                if (response) {
                    tenantPartitionsNumber = response;
                    scope.tenant.ruleEngineSettings.partitionsNumber = tenantPartitionsNumber;
                }
            }
        );

        var template = $templateCache.get(tenantFieldsetTemplate);
        element.html(template);

        scope.onTenantIdCopied = function() {
            toast.showSuccess($translate.instant('tenant.idCopiedMessage'), 750, angular.element(element).parent().parent(), 'bottom left');
        };

        $compile(element.contents())(scope);
    }
    return {
        restrict: "E",
        link: linker,
        scope: {
            tenant: '=',
            isEdit: '=',
            theForm: '=',
            onManageUsers: '&',
            onDeleteTenant: '&'
        }
    };
}
