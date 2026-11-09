import logging
import threading
import time

import pytest

from app.main import Config, Service, create_app


def wait_for(predicate, timeout=5.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.01)
    return False


def gen_records(caplog, level):
    return [r for r in caplog.records if r.name == "noisy.generator" and r.levelname == level]


@pytest.fixture
def service():
    svc = Service(Config(info_interval_ms=20, error_interval_ms=20))
    yield svc
    svc.stop()


@pytest.fixture
def client(service):
    return create_app(service).test_client()


def test_info_flows_without_errors_before_escalation(service, caplog):
    with caplog.at_level(logging.INFO):
        service.start()
        assert wait_for(lambda: len(gen_records(caplog, "INFO")) >= 3)
        assert gen_records(caplog, "ERROR") == []


def test_escalate_and_descalate_flow(service, client, caplog):
    with caplog.at_level(logging.INFO):
        service.start()
        assert wait_for(lambda: len(gen_records(caplog, "INFO")) >= 2)

        resp = client.post("/logs/escalate")
        assert resp.status_code == 200
        assert resp.get_json() == {"error_logging": True}
        assert wait_for(lambda: len(gen_records(caplog, "ERROR")) >= 3)

        resp = client.post("/logs/descalate")
        assert resp.get_json() == {"error_logging": False}
        errors_after_stop = len(gen_records(caplog, "ERROR"))

        # INFO continues after de-escalation while ERROR stays flat
        infos = len(gen_records(caplog, "INFO"))
        assert wait_for(lambda: len(gen_records(caplog, "INFO")) >= infos + 3)
        assert len(gen_records(caplog, "ERROR")) == errors_after_stop


def test_info_continues_during_escalation(service, client, caplog):
    with caplog.at_level(logging.INFO):
        service.start()
        client.post("/logs/escalate")
        infos = len(gen_records(caplog, "INFO"))
        assert wait_for(lambda: len(gen_records(caplog, "INFO")) >= infos + 3)


def test_endpoints_idempotent(client):
    for _ in range(3):
        assert client.post("/logs/escalate").get_json() == {"error_logging": True}
    assert client.get("/logs/status").get_json()["error_logging"] is True
    for _ in range(3):
        assert client.post("/logs/descalate").get_json() == {"error_logging": False}
    assert client.get("/logs/status").get_json()["error_logging"] is False


def test_descalate_without_escalate(client):
    assert client.post("/logs/descalate").status_code == 200


def test_status_and_health(client):
    status = client.get("/logs/status").get_json()
    assert status == {"error_logging": False, "info_interval_ms": 20, "error_interval_ms": 20}
    assert client.get("/healthz").status_code == 200


@pytest.mark.parametrize("method,path", [
    ("get", "/logs/escalate"), ("get", "/logs/descalate"),
    ("post", "/logs/status"), ("post", "/healthz"),
])
def test_method_not_allowed(client, method, path):
    assert getattr(client, method)(path).status_code == 405


def test_no_leaked_threads(service, client):
    service.start()
    client.post("/logs/escalate")
    service.stop()
    assert wait_for(lambda: not [t for t in threading.enumerate() if t.name.startswith("emitter-")])
