/*
 * Copyright © 2016-2019 The Thingsboard Authors
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

import logoSvg from '../../svg/logo_white.svg';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function LoginController(toast, loginService, userService, $window, $scope/*, $rootScope, $log, $translate*/) {
    var vm = this;

    vm.logoSvg = logoSvg;

    vm.user = {
        name: '用户名（电子邮件）',
        password: '密码'
    };

    vm.login = login;
    vm.fromLoad = fromLoad();
    vm.inputBlur = inputBlur();
    vm.inputfou = inputfou();

    function fromLoad() {
        var width = $window.innerWidth;
        if(width<768){
            $scope.imgHidden=true;
            $scope.mobileMdCard={
                "width":"100%",
                "height":"100%",
                "margin":"0"
            };
            $scope.mobileHr={
                "width":"100%"
            };
            $scope.mobileName={
                "width":"80%"
            };
            $scope.mobileInput={
                "width":"80%"
            };
            $scope.mobileButton={
                "width":"80%"
            };
            $scope.mobileForgot={
                "width":"200px"
            }
            if(width<340) {
                $scope.mobileTitle = {
                    "padding-left": "25px"
                };
                $scope.mobileName = {
                    "margin-left":"25px",
                    "width":"85%"
                };
                $scope.mobileButton={
                    "width":"80%",
                    "margin-left":"25px"
                };
                $scope.mobileForgot={
                    "width":"200px",
                    "margin":"8px 0 0 28px"
                }
            }
        }
    }

    function inputfou() {
        if($window.innerWidth<768) {
            $scope.mobileInput = {
                "width": "80%",
                "height": "45px",
                "text-indent": "10px",
                "border": "0",
                "border-left": "1px solid #CDC6C4",
                "outline": "medium",
                "color": "#999999",
                "font-size": "14px",
                "box-shadow": "none",
                "padding": "0"
            };
        }else{
            $scope.mobileInput = {
                "width": "265px",
                "height": "45px",
                "text-indent": "10px",
                "border": "0",
                "border-left": "1px solid #CDC6C4",
                "outline": "medium",
                "color": "#999999",
                "font-size": "14px",
                "box-shadow": "none",
                "padding": "0"
            };
        }
    }

    function inputBlur() {
        if($window.innerWidth<768) {
            $scope.mobileInput = {
                "width": "80%",
                "height": "45px",
                "text-indent":"10px",
                "border":"0",
                "border-left":"1px solid #CDC6C4",
                "outline":"medium",
                "color":"#CDC6C4",
                "font-size":"14px",
                "box-shadow":"none",
                "padding":"0"
            };
        }else{
            $scope.mobileInput = {
                "width": "265px",
                "height": "45px",
                "text-indent": "10px",
                "border": "0",
                "border-left": "1px solid #CDC6C4",
                "outline": "medium",
                "color": "#CDC6C4",
                "font-size": "14px",
                "box-shadow": "none",
                "padding": "0"
            };
        }
    }

    function doLogin() {
        loginService.login(vm.user).then(function success(response) {
            var token = response.data.token;
            var refreshToken = response.data.refreshToken;
            userService.setUserFromJwtToken(token, refreshToken, true);
        }, function fail(/*response*/) {
            /*if (response && response.data && response.data.message) {
                toast.showError(response.data.message);
            } else if (response && response.statusText) {
                toast.showError(response.statusText);
            } else {
                toast.showError($translate.instant('error.unknown-error'));
            }*/
        });
    }

    function login() {
        doLogin();
    }
}
