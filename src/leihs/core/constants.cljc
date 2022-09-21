(ns leihs.core.constants)

(def USER_SESSION_COOKIE_NAME "leihs-user-session")

(def ANTI_CSRF_TOKEN_COOKIE_NAME "leihs-anti-csrf-token")
(def ANTI_CSRF_TOKEN_HEADER_NAME "x-csrf-token")
(def ANTI_CSRF_TOKEN_FORM_PARAM_NAME "csrf-token")

(def PASSWORD_AUTHENTICATION_SYSTEM_ID "password")

(def HTTP_UNSAVE_METHODS #{:delete :patch :post :put})
(def HTTP_SAVE_METHODS #{:get :head :options :trace})

(def TAB-INDEX 1)

(def GENERAL_BUILDING_UUID "abae04c5-d767-425e-acc2-7ce04df645d1")
