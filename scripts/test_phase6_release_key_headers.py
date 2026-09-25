import unittest

from scripts.phase6_release_publisher import PublisherError, api_key_headers


class SupabaseKeyHeaderTest(unittest.TestCase):
    def test_modern_secret_key_uses_apikey_without_bearer_header(self):
        key = "sb_secret_example_component_key_1234567890"
        self.assertEqual({"apikey": key}, api_key_headers(key))

    def test_legacy_service_role_jwt_keeps_bearer_compatibility(self):
        key = "eyJlegacy-service-role-example-token"
        self.assertEqual(
            {"apikey": key, "Authorization": f"Bearer {key}"},
            api_key_headers(key),
        )

    def test_publishable_key_is_rejected(self):
        with self.assertRaises(PublisherError):
            api_key_headers("sb_publishable_example_public_key_1234567890")


if __name__ == "__main__":
    unittest.main()
